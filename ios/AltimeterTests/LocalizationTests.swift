import XCTest
@testable import Altimeter

final class LocalizationTests: XCTestCase {
    override func tearDown() {
        L10n.language = .system
        super.tearDown()
    }

    func testManualLanguagesResolveCoreInterfaceStrings() {
        let expectations: [(AppLanguage, String)] = [
            (.russian, "Главная"),
            (.english, "Home"),
            (.chineseSimplified, "主页"),
            (.french, "Accueil"),
        ]

        for (language, expectedHome) in expectations {
            L10n.language = language
            XCTAssertEqual(L10n.string("Главная"), expectedHome)
            XCTAssertNotEqual(L10n.string("section.track"), "section.track")
            XCTAssertNotEqual(L10n.string("format.pressure", 600.0), "format.pressure")
        }
    }

    func testLanguagePickerIncludesSystemAndFourTranslations() {
        XCTAssertEqual(AppLanguage.allCases.count, 5)
        XCTAssertTrue(AppLanguage.allCases.contains(.system))
        XCTAssertTrue(AppLanguage.allCases.contains(.chineseSimplified))
    }
}
