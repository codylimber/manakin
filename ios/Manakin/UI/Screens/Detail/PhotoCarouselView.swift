import SwiftUI

struct PhotoCarouselView: View {
    let photos: [SpeciesPhoto]
    let key: String
    let repository: PhenologyRepository

    @State private var currentPage = 0

    var body: some View {
        if photos.isEmpty { return AnyView(EmptyView()) }

        return AnyView(
            ZStack(alignment: .bottom) {
                TabView(selection: $currentPage) {
                    ForEach(Array(photos.enumerated()), id: \.offset) { index, photo in
                        ZStack(alignment: .bottomLeading) {
                            if let url = repository.getPhotoURL(key: key, filename: photo.file) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                    default:
                                        Color.gray.opacity(0.3)
                                    }
                                }
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .clipped()
                            } else {
                                Color.gray.opacity(0.2)
                            }

                            // Attribution overlay
                            if let attribution = photo.attribution, !attribution.isEmpty {
                                Text("\(attribution) (via iNaturalist)")
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.8))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color.black.opacity(0.5))
                            }
                        }
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                // Page indicator dots
                if photos.count > 1 {
                    HStack(spacing: 6) {
                        ForEach(0..<photos.count, id: \.self) { index in
                            Circle()
                                .fill(index == currentPage ? Color.white : Color.white.opacity(0.4))
                                .frame(width: 8, height: 8)
                        }
                    }
                    .padding(.bottom, 8)
                }
            }
        )
    }
}
