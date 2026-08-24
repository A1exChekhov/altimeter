import Foundation
import HealthKit

@MainActor
final class HealthService: ObservableObject {
    @Published private(set) var vitals = VitalSample()
    @Published private(set) var isAvailable = HKHealthStore.isHealthDataAvailable()
    @Published private(set) var hasRequestedAccess = UserDefaults.standard.bool(forKey: "healthAccessRequested")
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    var needsAuthorizationRefresh: Bool {
        UserDefaults.standard.integer(forKey: "healthAccessSchemaVersion") < 2
    }

    private let store = HKHealthStore()

    func requestAccess() async {
        guard isAvailable else {
            errorMessage = "HealthKit недоступен на этом устройстве."
            return
        }
        guard let heartRate = HKObjectType.quantityType(forIdentifier: .heartRate),
              let restingHeartRate = HKObjectType.quantityType(forIdentifier: .restingHeartRate),
              let oxygen = HKObjectType.quantityType(forIdentifier: .oxygenSaturation),
              let steps = HKObjectType.quantityType(forIdentifier: .stepCount),
              let activeEnergy = HKObjectType.quantityType(forIdentifier: .activeEnergyBurned) else { return }
        do {
            try await store.requestAuthorization(
                toShare: [],
                read: [heartRate, restingHeartRate, oxygen, steps, activeEnergy]
            )
            UserDefaults.standard.set(true, forKey: "healthAccessRequested")
            UserDefaults.standard.set(2, forKey: "healthAccessSchemaVersion")
            hasRequestedAccess = true
            await refresh()
        } catch {
            errorMessage = "Не удалось запросить доступ к Здоровью: \(error.localizedDescription)"
        }
    }

    func refresh() async {
        guard isAvailable, hasRequestedAccess else { return }
        isLoading = true
        defer { isLoading = false }
        async let heart = latestQuantity(.heartRate)
        async let restingHeart = latestQuantity(.restingHeartRate)
        async let oxygen = latestQuantity(.oxygenSaturation)
        async let steps = stepsToday()
        async let calories = activeCaloriesToday()
        let (heartSample, restingHeartSample, oxygenSample, stepsValue, caloriesValue) = await (
            heart,
            restingHeart,
            oxygen,
            steps,
            calories
        )
        let newestHeart = [heartSample, restingHeartSample]
            .compactMap { $0 }
            .max { $0.endDate < $1.endDate }

        if let heartSample = newestHeart {
            let unit = HKUnit.count().unitDivided(by: .minute())
            vitals.heartRateBPM = heartSample.quantity.doubleValue(for: unit)
            vitals.heartRateDate = heartSample.endDate
            vitals.heartRateSource = Self.sourceName(for: heartSample)
            vitals.heartRateSourceBundle = heartSample.sourceRevision.source.bundleIdentifier
        }
        if let oxygenSample {
            vitals.oxygenPercent = oxygenSample.quantity.doubleValue(for: .percent()) * 100
            vitals.oxygenDate = oxygenSample.endDate
            vitals.oxygenSource = Self.sourceName(for: oxygenSample)
            vitals.oxygenSourceBundle = oxygenSample.sourceRevision.source.bundleIdentifier
        }
        if let stepsValue {
            vitals.stepsToday = stepsValue
            vitals.stepsDate = Date()
        }
        if let caloriesValue {
            vitals.activeCaloriesToday = caloriesValue
            vitals.activeCaloriesDate = Date()
        }
    }

    private func latestQuantity(_ identifier: HKQuantityTypeIdentifier) async -> HKQuantitySample? {
        guard let type = HKObjectType.quantityType(forIdentifier: identifier) else { return nil }
        return await withCheckedContinuation { continuation in
            let sort = NSSortDescriptor(key: HKSampleSortIdentifierEndDate, ascending: false)
            let query = HKSampleQuery(
                sampleType: type,
                predicate: nil,
                limit: 1,
                sortDescriptors: [sort]
            ) { _, samples, _ in
                continuation.resume(returning: samples?.first as? HKQuantitySample)
            }
            store.execute(query)
        }
    }

    private func stepsToday() async -> Double? {
        await cumulativeValueToday(.stepCount, unit: .count())
    }

    private func activeCaloriesToday() async -> Double? {
        await cumulativeValueToday(.activeEnergyBurned, unit: .kilocalorie())
    }

    private func cumulativeValueToday(
        _ identifier: HKQuantityTypeIdentifier,
        unit: HKUnit
    ) async -> Double? {
        guard let type = HKObjectType.quantityType(forIdentifier: identifier) else { return nil }
        let now = Date()
        let start = Calendar.current.startOfDay(for: now)
        let predicate = HKQuery.predicateForSamples(
            withStart: start,
            end: now,
            options: .strictStartDate
        )
        return await withCheckedContinuation { continuation in
            let query = HKStatisticsQuery(
                quantityType: type,
                quantitySamplePredicate: predicate,
                options: .cumulativeSum
            ) { _, statistics, _ in
                let value = statistics?.sumQuantity()?.doubleValue(for: unit)
                continuation.resume(returning: value)
            }
            store.execute(query)
        }
    }

    private static func sourceName(for sample: HKQuantitySample) -> String {
        let source = sample.sourceRevision.source.name
        let bundle = sample.sourceRevision.source.bundleIdentifier
        if source.localizedCaseInsensitiveContains("garmin") ||
            bundle.localizedCaseInsensitiveContains("garmin") {
            return "Garmin Connect"
        }
        if let deviceName = sample.device?.name, !deviceName.isEmpty { return deviceName }
        return source
    }
}
