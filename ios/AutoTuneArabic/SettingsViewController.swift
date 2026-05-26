import UIKit

final class SettingsViewController: UIViewController {

    // MARK: - UI Elements

    private let correctionStrengthLabel: UILabel = {
        let l = UILabel()
        l.text = "قوة التصحيح"
        l.font = UIFont.boldSystemFont(ofSize: 17)
        l.textColor = .label
        l.textAlignment = .right
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let correctionStrengthValueLabel: UILabel = {
        let l = UILabel()
        l.text = "100%"
        l.font = UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .regular)
        l.textColor = .systemOrange
        l.textAlignment = .left
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let correctionStrengthSlider: UISlider = {
        let s = UISlider()
        s.minimumValue = 0
        s.maximumValue = 1
        s.value = 1.0
        s.tintColor = .systemOrange
        s.translatesAutoresizingMaskIntoConstraints = false
        return s
    }()

    private let correctionSpeedLabel: UILabel = {
        let l = UILabel()
        l.text = "سرعة التصحيح"
        l.font = UIFont.boldSystemFont(ofSize: 17)
        l.textColor = .label
        l.textAlignment = .right
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let correctionSpeedValueLabel: UILabel = {
        let l = UILabel()
        l.text = "60%"
        l.font = UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .regular)
        l.textColor = .systemOrange
        l.textAlignment = .left
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let correctionSpeedSlider: UISlider = {
        let s = UISlider()
        s.minimumValue = 0
        s.maximumValue = 1
        // Default correctionSpeed = 0.30, so value = (0.30 - 0.01) / 0.49 ≈ 0.592
        s.value = Float((0.30 - 0.01) / 0.49)
        s.tintColor = .systemOrange
        s.translatesAutoresizingMaskIntoConstraints = false
        return s
    }()

    private let bypassLabel: UILabel = {
        let l = UILabel()
        l.text = "وضع التمرير المباشر (Bypass)"
        l.font = UIFont.boldSystemFont(ofSize: 17)
        l.textColor = .label
        l.textAlignment = .right
        l.numberOfLines = 0
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let bypassSwitch: UISwitch = {
        let s = UISwitch()
        s.isOn = false
        s.onTintColor = .systemOrange
        s.translatesAutoresizingMaskIntoConstraints = false
        return s
    }()

    private let infoLabel: UILabel = {
        let l = UILabel()
        l.text = "الأوتوتيون العربي يصحح الطبقة الصوتية حسب المقام المختار. " +
                 "قوة التصحيح تتحكم في مقدار التصحيح التلقائي. " +
                 "سرعة التصحيح تتحكم في مدى سرعة الانتقال إلى النغمة المستهدفة. " +
                 "وضع Bypass يتجاوز التصحيح ويُمرّر الصوت مباشرةً."
        l.font = UIFont.systemFont(ofSize: 14)
        l.textColor = .secondaryLabel
        l.textAlignment = .right
        l.numberOfLines = 0
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let divider1 = SettingsViewController.makeDivider()
    private let divider2 = SettingsViewController.makeDivider()
    private let divider3 = SettingsViewController.makeDivider()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = "إعدادات"

        setupLayout()
        wireActions()
    }

    // MARK: - Layout

    private func setupLayout() {
        let safe = view.safeAreaLayoutGuide

        // Correction Strength row header
        let strengthHeaderRow = makeHeaderRow(label: correctionStrengthLabel, value: correctionStrengthValueLabel)

        // Correction Speed row header
        let speedHeaderRow = makeHeaderRow(label: correctionSpeedLabel, value: correctionSpeedValueLabel)

        // Bypass row
        let bypassRow = UIView()
        bypassRow.translatesAutoresizingMaskIntoConstraints = false
        bypassRow.addSubview(bypassLabel)
        bypassRow.addSubview(bypassSwitch)
        NSLayoutConstraint.activate([
            bypassLabel.topAnchor.constraint(equalTo: bypassRow.topAnchor),
            bypassLabel.bottomAnchor.constraint(equalTo: bypassRow.bottomAnchor),
            bypassLabel.trailingAnchor.constraint(equalTo: bypassRow.trailingAnchor),
            bypassLabel.leadingAnchor.constraint(equalTo: bypassSwitch.trailingAnchor, constant: 12),

            bypassSwitch.centerYAnchor.constraint(equalTo: bypassRow.centerYAnchor),
            bypassSwitch.leadingAnchor.constraint(equalTo: bypassRow.leadingAnchor),
        ])

        let allViews: [UIView] = [
            strengthHeaderRow,
            correctionStrengthSlider,
            divider1,
            speedHeaderRow,
            correctionSpeedSlider,
            divider2,
            bypassRow,
            divider3,
            infoLabel,
        ]

        allViews.forEach { view.addSubview($0) }

        NSLayoutConstraint.activate([
            strengthHeaderRow.topAnchor.constraint(equalTo: safe.topAnchor, constant: 24),
            strengthHeaderRow.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            strengthHeaderRow.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            correctionStrengthSlider.topAnchor.constraint(equalTo: strengthHeaderRow.bottomAnchor, constant: 8),
            correctionStrengthSlider.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            correctionStrengthSlider.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            divider1.topAnchor.constraint(equalTo: correctionStrengthSlider.bottomAnchor, constant: 20),
            divider1.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            divider1.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            divider1.heightAnchor.constraint(equalToConstant: 1),

            speedHeaderRow.topAnchor.constraint(equalTo: divider1.bottomAnchor, constant: 20),
            speedHeaderRow.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            speedHeaderRow.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            correctionSpeedSlider.topAnchor.constraint(equalTo: speedHeaderRow.bottomAnchor, constant: 8),
            correctionSpeedSlider.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            correctionSpeedSlider.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            divider2.topAnchor.constraint(equalTo: correctionSpeedSlider.bottomAnchor, constant: 20),
            divider2.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            divider2.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            divider2.heightAnchor.constraint(equalToConstant: 1),

            bypassRow.topAnchor.constraint(equalTo: divider2.bottomAnchor, constant: 20),
            bypassRow.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            bypassRow.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            divider3.topAnchor.constraint(equalTo: bypassRow.bottomAnchor, constant: 20),
            divider3.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            divider3.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            divider3.heightAnchor.constraint(equalToConstant: 1),

            infoLabel.topAnchor.constraint(equalTo: divider3.bottomAnchor, constant: 20),
            infoLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            infoLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
        ])
    }

    private func makeHeaderRow(label: UILabel, value: UILabel) -> UIView {
        let row = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false
        row.addSubview(label)
        row.addSubview(value)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: row.topAnchor),
            label.bottomAnchor.constraint(equalTo: row.bottomAnchor),
            label.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            label.leadingAnchor.constraint(equalTo: value.trailingAnchor, constant: 8),

            value.topAnchor.constraint(equalTo: row.topAnchor),
            value.bottomAnchor.constraint(equalTo: row.bottomAnchor),
            value.leadingAnchor.constraint(equalTo: row.leadingAnchor),
            value.widthAnchor.constraint(equalToConstant: 52),
        ])
        return row
    }

    private static func makeDivider() -> UIView {
        let v = UIView()
        v.backgroundColor = UIColor.systemGray4
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }

    // MARK: - Wire Actions

    private func wireActions() {
        correctionStrengthSlider.addTarget(self,
                                           action: #selector(strengthChanged(_:)),
                                           for: .valueChanged)
        correctionSpeedSlider.addTarget(self,
                                        action: #selector(speedChanged(_:)),
                                        for: .valueChanged)
        bypassSwitch.addTarget(self,
                               action: #selector(bypassChanged(_:)),
                               for: .valueChanged)
    }

    // MARK: - Slider/Switch Callbacks

    @objc private func strengthChanged(_ sender: UISlider) {
        let val = Double(sender.value)
        AudioEngine.shared.sensitivity = val
        correctionStrengthValueLabel.text = "\(Int(round(val * 100)))%"
    }

    @objc private func speedChanged(_ sender: UISlider) {
        let val = Double(sender.value)
        let speed = 0.01 + val * 0.49
        AudioEngine.shared.correctionSpeed = speed
        correctionSpeedValueLabel.text = "\(Int(round(val * 100)))%"
    }

    @objc private func bypassChanged(_ sender: UISwitch) {
        AudioEngine.shared.bypassMode = sender.isOn
    }
}
