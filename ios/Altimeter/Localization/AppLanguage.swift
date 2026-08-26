import Foundation

enum AppLanguage: String, CaseIterable, Identifiable {
    case system
    case russian
    case english
    case chineseSimplified
    case french

    var id: Self { self }

    var title: String {
        switch self {
        case .system: L10n.string("language.system")
        case .russian: "Русский"
        case .english: "English"
        case .chineseSimplified: "简体中文"
        case .french: "Français"
        }
    }

    var locale: Locale {
        guard let localizationIdentifier else { return .autoupdatingCurrent }
        return Locale(identifier: localizationIdentifier)
    }

    fileprivate var localizationIdentifier: String? {
        switch self {
        case .system: nil
        case .russian: "ru"
        case .english: "en"
        case .chineseSimplified: "zh-Hans"
        case .french: "fr"
        }
    }
}

enum L10n {
    static var language: AppLanguage = .system

    static func string(_ key: String, _ arguments: CVarArg...) -> String {
        let bundle = localizedBundle
        let format = bundle.localizedString(forKey: key, value: key, table: nil)
        guard !arguments.isEmpty else { return format }
        return String(format: format, locale: language.locale, arguments: arguments)
    }

    private static var localizedBundle: Bundle {
        guard let identifier = language.localizationIdentifier,
              let path = Bundle.main.path(forResource: identifier, ofType: "lproj"),
              let bundle = Bundle(path: path) else {
            return .main
        }
        return bundle
    }
}
