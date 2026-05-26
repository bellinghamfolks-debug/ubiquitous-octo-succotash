import UIKit

final class MaqamViewController: UIViewController {

    // MARK: - Data

    private let maqams = MaqamLibrary.buildAll()
    private var selectedMaqamIndex = 0
    private var selectedRootIndex = 2   // مِ = 349.23 Hz

    // MARK: - UI

    private let maqamHeaderLabel: UILabel = {
        let l = UILabel()
        l.text = "اختر المقام"
        l.font = UIFont.boldSystemFont(ofSize: 20)
        l.textColor = .systemOrange
        l.textAlignment = .right
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    private let rootHeaderLabel: UILabel = {
        let l = UILabel()
        l.text = "نغمة الجذر"
        l.font = UIFont.boldSystemFont(ofSize: 20)
        l.textColor = .systemOrange
        l.textAlignment = .right
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    // Maqam grid via UICollectionView
    private lazy var collectionView: UICollectionView = {
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 8
        layout.minimumLineSpacing = 8
        layout.sectionInset = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        let cv = UICollectionView(frame: .zero, collectionViewLayout: layout)
        cv.backgroundColor = .clear
        cv.translatesAutoresizingMaskIntoConstraints = false
        cv.isScrollEnabled = false
        return cv
    }()

    // Root note buttons
    private var rootButtons: [UIButton] = []
    private let rootScrollView: UIScrollView = {
        let sv = UIScrollView()
        sv.translatesAutoresizingMaskIntoConstraints = false
        sv.showsHorizontalScrollIndicator = false
        return sv
    }()
    private let rootStackView: UIStackView = {
        let sv = UIStackView()
        sv.axis = .horizontal
        sv.spacing = 10
        sv.alignment = .center
        sv.translatesAutoresizingMaskIntoConstraints = false
        return sv
    }()

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = "مقامات"

        setupCollectionView()
        setupRootButtons()
        setupLayout()
        applyInitialSelection()
    }

    // MARK: - Setup

    private func setupCollectionView() {
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.register(MaqamCell.self, forCellWithReuseIdentifier: MaqamCell.reuseID)
    }

    private func setupRootButtons() {
        for (index, name) in MaqamLibrary.rootNamesAr.enumerated() {
            let btn = UIButton(type: .system)
            btn.setTitle(name, for: .normal)
            btn.titleLabel?.font = UIFont.boldSystemFont(ofSize: 18)
            btn.layer.cornerRadius = 10
            btn.layer.masksToBounds = true
            btn.contentEdgeInsets = UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
            btn.tag = index
            btn.addTarget(self, action: #selector(rootButtonTapped(_:)), for: .touchUpInside)
            styleRootButton(btn, selected: false)
            rootButtons.append(btn)
            rootStackView.addArrangedSubview(btn)
        }
    }

    private func setupLayout() {
        view.addSubview(maqamHeaderLabel)
        view.addSubview(collectionView)
        view.addSubview(rootHeaderLabel)
        view.addSubview(rootScrollView)
        rootScrollView.addSubview(rootStackView)

        let safe = view.safeAreaLayoutGuide

        NSLayoutConstraint.activate([
            // Maqam header
            maqamHeaderLabel.topAnchor.constraint(equalTo: safe.topAnchor, constant: 16),
            maqamHeaderLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            maqamHeaderLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Collection view for maqam grid
            collectionView.topAnchor.constraint(equalTo: maqamHeaderLabel.bottomAnchor, constant: 12),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            // Height = 5 rows of ~60pt + spacing
            collectionView.heightAnchor.constraint(equalToConstant: 340),

            // Root header
            rootHeaderLabel.topAnchor.constraint(equalTo: collectionView.bottomAnchor, constant: 20),
            rootHeaderLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            rootHeaderLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            // Root scroll view
            rootScrollView.topAnchor.constraint(equalTo: rootHeaderLabel.bottomAnchor, constant: 12),
            rootScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            rootScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            rootScrollView.heightAnchor.constraint(equalToConstant: 50),

            // Root stack view inside scroll view
            rootStackView.topAnchor.constraint(equalTo: rootScrollView.topAnchor),
            rootStackView.bottomAnchor.constraint(equalTo: rootScrollView.bottomAnchor),
            rootStackView.leadingAnchor.constraint(equalTo: rootScrollView.leadingAnchor),
            rootStackView.trailingAnchor.constraint(equalTo: rootScrollView.trailingAnchor),
            rootStackView.heightAnchor.constraint(equalTo: rootScrollView.heightAnchor),
        ])
    }

    private func applyInitialSelection() {
        // Default: بياتي (index 0), root مِ (index 2)
        AudioEngine.shared.activeMaqam = maqams[selectedMaqamIndex]
        AudioEngine.shared.rootHz = MaqamLibrary.rootFreqs[selectedRootIndex]
        styleRootButton(rootButtons[selectedRootIndex], selected: true)
    }

    // MARK: - Actions

    @objc private func rootButtonTapped(_ sender: UIButton) {
        let index = sender.tag
        guard index != selectedRootIndex else { return }

        styleRootButton(rootButtons[selectedRootIndex], selected: false)
        selectedRootIndex = index
        styleRootButton(rootButtons[selectedRootIndex], selected: true)

        AudioEngine.shared.rootHz = MaqamLibrary.rootFreqs[selectedRootIndex]
    }

    // MARK: - Helpers

    private func styleRootButton(_ btn: UIButton, selected: Bool) {
        if selected {
            btn.backgroundColor = .systemOrange
            btn.setTitleColor(.white, for: .normal)
        } else {
            btn.backgroundColor = UIColor.systemGray5
            btn.setTitleColor(.label, for: .normal)
        }
    }
}

// MARK: - UICollectionViewDataSource

extension MaqamViewController: UICollectionViewDataSource {

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return maqams.count
    }

    func collectionView(_ collectionView: UICollectionView,
                        cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: MaqamCell.reuseID,
                                                      for: indexPath) as! MaqamCell
        let maqam = maqams[indexPath.item]
        let isSelected = indexPath.item == selectedMaqamIndex
        cell.configure(maqam: maqam, isSelected: isSelected)
        return cell
    }
}

// MARK: - UICollectionViewDelegate

extension MaqamViewController: UICollectionViewDelegate {

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        guard indexPath.item != selectedMaqamIndex else { return }

        let oldIndex = selectedMaqamIndex
        selectedMaqamIndex = indexPath.item

        // Refresh old and new cells
        collectionView.reloadItems(at: [IndexPath(item: oldIndex, section: 0), indexPath])

        AudioEngine.shared.activeMaqam = maqams[selectedMaqamIndex]
    }
}

// MARK: - UICollectionViewDelegateFlowLayout

extension MaqamViewController: UICollectionViewDelegateFlowLayout {

    func collectionView(_ collectionView: UICollectionView,
                        layout collectionViewLayout: UICollectionViewLayout,
                        sizeForItemAt indexPath: IndexPath) -> CGSize {
        let totalWidth = collectionView.bounds.width
        let spacing: CGFloat = 8 * 2  // 2 gaps between 3 columns
        let itemWidth = (totalWidth - spacing) / 3
        return CGSize(width: itemWidth, height: 60)
    }
}

// MARK: - MaqamCell

private final class MaqamCell: UICollectionViewCell {

    static let reuseID = "MaqamCell"

    private let label: UILabel = {
        let l = UILabel()
        l.textAlignment = .center
        l.font = UIFont.boldSystemFont(ofSize: 16)
        l.adjustsFontSizeToFitWidth = true
        l.minimumScaleFactor = 0.7
        l.translatesAutoresizingMaskIntoConstraints = false
        return l
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.layer.cornerRadius = 10
        contentView.layer.masksToBounds = true
        contentView.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
            label.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(maqam: Maqam, isSelected: Bool) {
        label.text = maqam.nameAr
        if isSelected {
            contentView.backgroundColor = maqam.color
            label.textColor = .white
            contentView.layer.borderWidth = 2
            contentView.layer.borderColor = UIColor.white.cgColor
        } else {
            contentView.backgroundColor = maqam.color.withAlphaComponent(0.25)
            label.textColor = maqam.color
            contentView.layer.borderWidth = 1
            contentView.layer.borderColor = maqam.color.cgColor
        }
    }
}
