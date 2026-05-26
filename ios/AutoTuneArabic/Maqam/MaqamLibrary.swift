import UIKit

struct MaqamLibrary {
    static let rootNamesAr = ["د", "رَ", "مِ", "فَ", "صَ", "لَ", "سِ"]
    static let rootFreqs: [Double] = [293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25]

    static func buildAll() -> [Maqam] {
        return [
            Maqam(
                nameAr: "بياتي",
                cents: [0, 150, 300, 500, 700, 800, 1000, 1200],
                color: .systemOrange
            ),
            Maqam(
                nameAr: "راست",
                cents: [0, 200, 350, 500, 700, 900, 1050, 1200],
                color: .systemYellow
            ),
            Maqam(
                nameAr: "حجاز",
                cents: [0, 100, 350, 500, 700, 800, 1050, 1200],
                color: .systemPurple
            ),
            Maqam(
                nameAr: "صبا",
                cents: [0, 150, 300, 450, 650, 800, 1000, 1200],
                color: .systemBlue
            ),
            Maqam(
                nameAr: "كرد",
                cents: [0, 100, 300, 500, 700, 800, 1000, 1200],
                color: .systemTeal
            ),
            Maqam(
                nameAr: "نهاوند",
                cents: [0, 200, 300, 500, 700, 800, 1100, 1200],
                color: .systemGreen
            ),
            Maqam(
                nameAr: "عجم",
                cents: [0, 200, 400, 500, 700, 900, 1100, 1200],
                color: UIColor(red: 0.0, green: 0.78, blue: 0.69, alpha: 1.0) // mint
            ),
            Maqam(
                nameAr: "جهاركاه",
                cents: [0, 200, 350, 500, 700, 900, 1050, 1200],
                color: .systemCyan
            ),
            Maqam(
                nameAr: "نوى أثر",
                cents: [0, 200, 300, 600, 700, 800, 1100, 1200],
                color: .systemIndigo
            ),
            Maqam(
                nameAr: "نكريز",
                cents: [0, 200, 300, 600, 700, 900, 1000, 1200],
                color: .systemBrown
            ),
            Maqam(
                nameAr: "سيكاه",
                cents: [0, 150, 350, 500, 700, 850, 1050, 1200],
                color: .systemPink
            ),
            Maqam(
                nameAr: "حسيني",
                cents: [0, 150, 300, 500, 700, 800, 1000, 1200],
                color: .systemRed
            ),
            Maqam(
                nameAr: "عشيران",
                cents: [0, 150, 300, 500, 700, 800, 1000, 1200],
                color: UIColor.systemOrange.withAlphaComponent(0.6)
            ),
        ]
    }
}
