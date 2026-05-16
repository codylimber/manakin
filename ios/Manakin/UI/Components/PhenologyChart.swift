import SwiftUI

struct PhenologyChart: View {
    let weekly: [WeeklyEntry]
    let currentWeek: Int
    let peakWeek: Int

    private static let months = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]
    private static let monthWeeks = [1, 5, 9, 14, 18, 22, 27, 31, 35, 40, 44, 48]

    @Environment(\.appColors) private var colors

    var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            let labelHeight: CGFloat = 28
            let chartHeight = h - labelHeight
            let barWidth = w / 53.0
            let gap = barWidth * 0.1

            // Grid lines at 25%, 50%, 75%
            for frac in [0.25, 0.5, 0.75] as [CGFloat] {
                let y = chartHeight * (1.0 - frac)
                var gridPath = Path()
                gridPath.move(to: CGPoint(x: 0, y: y))
                gridPath.addLine(to: CGPoint(x: w, y: y))
                context.stroke(
                    gridPath,
                    with: .color(.white.opacity(0.08)),
                    lineWidth: 1
                )
            }

            // Bars
            for entry in weekly {
                guard entry.week >= 1, entry.week <= 53 else { continue }
                let x = CGFloat(entry.week - 1) * barWidth
                let barH = CGFloat(entry.relAbundance) * chartHeight
                guard barH > 0.5 else { continue }
                let isPeak = entry.week == peakWeek
                let alpha = isPeak ? 1.0 : 0.3 + 0.7 * Double(entry.relAbundance)
                let rect = CGRect(
                    x: x + gap / 2,
                    y: chartHeight - barH,
                    width: barWidth - gap,
                    height: barH
                )
                context.fill(
                    Path(rect),
                    with: .color(Color.appPrimary.opacity(alpha))
                )
            }

            // Current week dashed line
            if currentWeek >= 1, currentWeek <= 53 {
                let cx = (CGFloat(currentWeek) - 0.5) * barWidth
                var dashPath = Path()
                dashPath.move(to: CGPoint(x: cx, y: 0))
                dashPath.addLine(to: CGPoint(x: cx, y: chartHeight))
                context.stroke(
                    dashPath,
                    with: .color(.white.opacity(0.8)),
                    style: StrokeStyle(lineWidth: 2, dash: [6, 4])
                )
            }

            // Month labels
            for i in Self.months.indices {
                let x = (CGFloat(Self.monthWeeks[i]) - 0.5) * barWidth
                let text = Text(Self.months[i])
                    .font(.system(size: 10))
                    .foregroundColor(colors.onSurfaceVariant)
                context.draw(
                    context.resolve(text),
                    at: CGPoint(x: x, y: h - 8),
                    anchor: .center
                )
            }
        }
        .frame(height: 120)
    }
}
