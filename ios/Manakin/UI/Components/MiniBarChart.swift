import SwiftUI

struct MiniBarChart: View {
    let weekly: [WeeklyEntry]
    let currentWeek: Int
    var barColor: Color = .appPrimary

    var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let barWidth = w / 53.0
            let gap = barWidth * 0.15

            // Draw bars
            for entry in weekly {
                guard entry.week >= 1, entry.week <= 53 else { continue }
                let x = CGFloat(entry.week - 1) * barWidth
                let barH = CGFloat(entry.relAbundance) * h
                guard barH > 0.5 else { continue }
                let alpha = 0.3 + 0.7 * Double(entry.relAbundance)
                let rect = CGRect(
                    x: x + gap / 2,
                    y: h - barH,
                    width: barWidth - gap,
                    height: barH
                )
                context.fill(
                    Path(rect),
                    with: .color(barColor.opacity(alpha))
                )
            }

            // Current week indicator
            if currentWeek >= 1, currentWeek <= 53 {
                let cx = (CGFloat(currentWeek) - 0.5) * barWidth
                var path = Path()
                path.move(to: CGPoint(x: cx, y: 0))
                path.addLine(to: CGPoint(x: cx, y: h))
                context.stroke(
                    path,
                    with: .color(.white.opacity(0.7)),
                    lineWidth: 1.5
                )
            }
        }
        .frame(height: 20)
    }
}
