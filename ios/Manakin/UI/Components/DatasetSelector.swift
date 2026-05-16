import SwiftUI

struct DatasetItem: Identifiable {
    let key: String
    let group: String
    let placeName: String

    var id: String { key }
}

struct DatasetSelector: View {
    let datasets: [DatasetItem]
    let selectedKeys: Set<String>
    let onSelectSingle: (String) -> Void
    let onToggle: (String) -> Void
    var onSelectAll: (() -> Void)?

    @State private var isExpanded = false
    @Environment(\.appColors) private var colors

    private var displayLabel: String {
        if selectedKeys.count == datasets.count, onSelectAll != nil {
            return "All Datasets"
        } else if selectedKeys.count == 1, let key = selectedKeys.first,
                  let item = datasets.first(where: { $0.key == key }) {
            return "\(item.group) \u{2014} \(item.placeName)"
        } else {
            return "\(selectedKeys.count) datasets selected"
        }
    }

    var body: some View {
        Menu {
            if let onSelectAll {
                Button {
                    onSelectAll()
                } label: {
                    Label("All Datasets", systemImage: selectedKeys.count == datasets.count ? "checkmark.circle.fill" : "circle")
                }

                Divider()
            }

            ForEach(datasets) { item in
                Button {
                    onToggle(item.key)
                } label: {
                    Label {
                        VStack(alignment: .leading) {
                            Text(item.group)
                                .fontWeight(selectedKeys.contains(item.key) ? .bold : .regular)
                            Text(item.placeName)
                                .font(.caption)
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    } icon: {
                        Image(systemName: selectedKeys.contains(item.key) ? "checkmark.circle.fill" : "circle")
                    }
                }
            }
        } label: {
            HStack {
                Text(displayLabel)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(colors.onBackground)
                    .lineLimit(1)
                Spacer()
                Image(systemName: "chevron.down")
                    .foregroundColor(.appPrimary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
