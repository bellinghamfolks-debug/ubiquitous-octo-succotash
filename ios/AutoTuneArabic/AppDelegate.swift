import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {

        // Force RTL layout for Arabic UI
        UIView.appearance().semanticContentAttribute = .forceRightToLeft

        // Navigation bar appearance
        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithOpaqueBackground()
        navAppearance.backgroundColor = .systemBackground
        navAppearance.titleTextAttributes = [
            .foregroundColor: UIColor.label,
            .font: UIFont.boldSystemFont(ofSize: 18)
        ]
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance
        UINavigationBar.appearance().tintColor = .systemOrange

        // Tab bar appearance
        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithOpaqueBackground()
        UITabBar.appearance().standardAppearance = tabAppearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = tabAppearance
        }
        UITabBar.appearance().tintColor = .systemOrange

        // Build view controllers
        let performanceVC = makeNav(root: PerformanceViewController(),
                                    title: "أداء",
                                    image: UIImage(systemName: "waveform.circle.fill"))

        let maqamVC = makeNav(root: MaqamViewController(),
                              title: "مقامات",
                              image: UIImage(systemName: "music.note.list"))

        let settingsVC = makeNav(root: SettingsViewController(),
                                 title: "إعدادات",
                                 image: UIImage(systemName: "slider.horizontal.3"))

        let recordingsVC = makeNav(root: RecordingsViewController(),
                                   title: "تسجيلات",
                                   image: UIImage(systemName: "folder.fill"))

        // Tab bar controller
        let tabBarController = UITabBarController()
        tabBarController.viewControllers = [performanceVC, maqamVC, settingsVC, recordingsVC]
        tabBarController.selectedIndex = 0

        // Window
        let win = UIWindow(frame: UIScreen.main.bounds)
        win.rootViewController = tabBarController
        win.makeKeyAndVisible()
        window = win

        return true
    }

    // MARK: - Helpers

    private func makeNav(root: UIViewController, title: String, image: UIImage?) -> UINavigationController {
        root.title = title
        let nav = UINavigationController(rootViewController: root)
        nav.tabBarItem = UITabBarItem(title: title, image: image, selectedImage: image)
        return nav
    }
}
