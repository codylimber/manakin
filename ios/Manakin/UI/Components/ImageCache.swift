import UIKit

class ImageCache {
    static let shared = ImageCache()
    private var cache = NSCache<NSString, UIImage>()

    func image(for path: String) -> UIImage? {
        if let cached = cache.object(forKey: path as NSString) {
            return cached
        }
        guard let image = UIImage(contentsOfFile: path) else { return nil }
        cache.setObject(image, forKey: path as NSString)
        return image
    }
}
