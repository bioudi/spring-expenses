package com.expensetracker.service;

import com.expensetracker.dto.DashboardResponse;
import com.expensetracker.dto.DashboardResponse.CategoryBreakdown;
import com.expensetracker.dto.DashboardResponse.DailySpending;
import com.expensetracker.dto.DashboardResponse.MerchantSummary;
import com.expensetracker.dto.DashboardResponse.PeriodSummary;
import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyInsightsService {

    private final ExpenseService expenseService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${insights.email.enabled:true}")
    private boolean emailEnabled;

    // Category colors matching the frontend CATEGORY_COLORS in categories.ts
    private static final Map<String, String> CATEGORY_COLORS = Map.ofEntries(
            Map.entry("Groceries", "#58a6ff"),
            Map.entry("Restaurants", "#3b82f6"),
            Map.entry("Coffee & Cafes", "#8b5cf6"),
            Map.entry("Fast Food", "#f97316"),
            Map.entry("Bars & Alcohol", "#ef4444"),
            Map.entry("Gas & Fuel", "#eab308"),
            Map.entry("Public Transit", "#06b6d4"),
            Map.entry("Rideshare & Taxi", "#14b8a6"),
            Map.entry("Parking", "#6366f1"),
            Map.entry("Car Maintenance", "#a855f7"),
            Map.entry("Rent & Mortgage", "#ec4899"),
            Map.entry("Utilities", "#f43f5e"),
            Map.entry("Internet & Phone", "#10b981"),
            Map.entry("Insurance", "#0ea5e9"),
            Map.entry("Clothing", "#d946ef"),
            Map.entry("Electronics", "#2563eb"),
            Map.entry("Home & Garden", "#16a34a"),
            Map.entry("Pharmacy", "#dc2626"),
            Map.entry("Doctor & Dental", "#059669"),
            Map.entry("Gym & Fitness", "#7c3aed"),
            Map.entry("Subscriptions", "#0891b2"),
            Map.entry("Streaming", "#9333ea"),
            Map.entry("Movies & Events", "#c026d3"),
            Map.entry("Gaming", "#4f46e5"),
            Map.entry("Flights", "#0284c7"),
            Map.entry("Hotels", "#b45309"),
            Map.entry("Car Rental", "#0d9488"),
            Map.entry("Education", "#4338ca"),
            Map.entry("Gifts & Donations", "#be185d"),
            Map.entry("Pet Care", "#65a30d"),
            Map.entry("Personal Care", "#ea580c"),
            Map.entry("Bank & Fees", "#64748b"),
            Map.entry("Other", "#6b7280"),
            Map.entry("Uncategorized", "#484f58")
    );

    private static final String DEFAULT_CATEGORY_COLOR = "#6b7280";

    // Design tokens matching the app's light theme (index.css :root)
    private static final String BG_PAGE = "#f5f5f5";
    private static final String BG_CARD = "#ffffff";
    private static final String FG = "#0f0f0f";
    private static final String FG_MUTED = "#636363";
    private static final String BORDER = "#e5e5e5";
    private static final String FONT_STACK = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif";
    private static final String RADIUS = "10px";

    @Scheduled(cron = "0 0 9 L * ?")
    public void sendMonthlyInsights() {
        if (!emailEnabled) {
            log.info("Monthly insights email is disabled. Skipping.");
            return;
        }

        log.info("Starting monthly insights email generation...");

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            log.warn("No users found. Skipping monthly insights.");
            return;
        }

        LocalDate today = LocalDate.now();
        String monthName = today.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        for (User user : users) {
            try {
                // Get current month's data for this user
                DashboardResponse dashboard = expenseService.getDashboard(today, user.getId());
                PeriodSummary currentMonth = dashboard.getMonth();

                // Skip users with no transactions this month
                if (currentMonth.getTransactionCount() == 0) {
                    log.info("Skipping monthly insights for user '{}' — no transactions this month", user.getEmail());
                    continue;
                }

                // Get previous month's data for comparison
                LocalDate previousMonthDate = today.minusMonths(1);
                DashboardResponse previousDashboard = expenseService.getDashboard(previousMonthDate, user.getId());
                PeriodSummary previousMonth = previousDashboard.getMonth();

                String subject = "Your Expense Insights \u2014 " + monthName;
                String htmlBody = buildEmailHtml(currentMonth, previousMonth, monthName);

                emailService.sendHtmlEmail(new String[]{user.getEmail()}, subject, htmlBody);
                log.info("Monthly insights email sent successfully for user '{}' ({})", user.getEmail(), monthName);

            } catch (Exception e) {
                log.error("Failed to send monthly insights email for user '{}': {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }

    private String buildEmailHtml(PeriodSummary current, PeriodSummary previous, String monthName) {
        StringBuilder html = new StringBuilder();

        BigDecimal totalSpent = current.getTotalSpent();
        long txCount = current.getTransactionCount();
        BigDecimal avgPerTx = current.getAvgPerTransaction();

        // Average daily spending
        long daysInMonth = current.getDailySpending() != null ? current.getDailySpending().size() : 30;
        BigDecimal avgDaily = daysInMonth > 0 && totalSpent.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Highest spending day
        DailySpending highestDay = null;
        if (current.getDailySpending() != null) {
            highestDay = current.getDailySpending().stream()
                    .filter(d -> d.getTotal().compareTo(BigDecimal.ZERO) > 0)
                    .max(Comparator.comparing(DailySpending::getTotal))
                    .orElse(null);
        }

        // Previous month comparison
        BigDecimal prevTotal = previous != null ? previous.getTotalSpent() : BigDecimal.ZERO;
        BigDecimal monthDiff = totalSpent.subtract(prevTotal);
        boolean spentMore = monthDiff.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal diffPercentage = prevTotal.compareTo(BigDecimal.ZERO) > 0
                ? monthDiff.abs().multiply(BigDecimal.valueOf(100))
                    .divide(prevTotal, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Start HTML
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("</head>");
        html.append("<body style='margin:0;padding:0;background-color:").append(BG_PAGE).append(";font-family:").append(FONT_STACK).append(";'>");

        // Container
        html.append("<div style='max-width:600px;margin:24px auto;'>");

        // Header — clean, minimal brand bar matching the app sidebar header
        html.append("<div style='padding:24px 24px 0 24px;'>");
        html.append("<table cellpadding='0' cellspacing='0'><tr>");
        html.append("<td style='width:28px;height:28px;background-color:").append(FG).append(";border-radius:6px;text-align:center;vertical-align:middle;'>");
        html.append("<span style='color:#ffffff;font-size:14px;font-weight:700;'>S</span>");
        html.append("</td>");
        html.append("<td style='padding-left:10px;'>");
        html.append("<span style='font-size:15px;font-weight:600;color:").append(FG).append(";letter-spacing:-0.01em;'>Spendifi</span>");
        html.append("</td>");
        html.append("</tr></table>");
        html.append("</div>");

        // Title section
        html.append("<div style='padding:20px 24px 4px 24px;'>");
        html.append("<h1 style='margin:0;font-size:22px;font-weight:700;color:").append(FG).append(";'>Monthly Insights</h1>");
        html.append("<p style='margin:4px 0 0 0;font-size:14px;color:").append(FG_MUTED).append(";'>").append(esc(monthName)).append("</p>");
        html.append("</div>");

        // Stat cards — 2 rows matching the app's Card component style
        html.append("<div style='padding:16px 24px 0 24px;'>");

        // Row 1: Total Spent, Transactions, Avg/Transaction
        html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:12px;'><tr>");
        html.append(statCardTd("Total Spent", "$" + fmt(totalSpent), esc(monthName)));
        html.append("<td width='12'></td>");
        html.append(statCardTd("Transactions", String.valueOf(txCount), esc(monthName)));
        html.append("<td width='12'></td>");
        html.append(statCardTd("Avg / Transaction", "$" + fmt(avgPerTx),
                txCount + " transaction" + (txCount != 1 ? "s" : "") + " this period"));
        html.append("</tr></table>");

        // Row 2: Avg Daily, Highest Day
        html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:12px;'><tr>");
        html.append(statCardTd("Avg Daily", "$" + fmt(avgDaily), daysInMonth + " days tracked"));
        html.append("<td width='12'></td>");
        if (highestDay != null) {
            String dayLabel = highestDay.getDate().format(DateTimeFormatter.ofPattern("MMM d"));
            html.append(statCardTd("Highest Day", "$" + fmt(highestDay.getTotal()), dayLabel));
        } else {
            html.append(statCardTd("Highest Day", "$0.00", "No data"));
        }
        html.append("</tr></table>");

        html.append("</div>");

        // Previous month comparison — card with left accent border
        if (prevTotal.compareTo(BigDecimal.ZERO) > 0) {
            String arrow = spentMore ? "&#9650;" : "&#9660;";
            String accentColor = spentMore ? "#ef4444" : "#10b981";
            String verb = spentMore ? "more" : "less";
            html.append("<div style='padding:0 24px;margin-bottom:16px;'>");
            html.append("<div style='background-color:").append(BG_CARD).append(";border:1px solid ").append(BORDER).append(";border-left:4px solid ").append(accentColor).append(";border-radius:").append(RADIUS).append(";padding:16px 20px;'>");
            html.append("<p style='color:").append(FG_MUTED).append(";margin:0;font-size:13px;'>vs. Previous Month</p>");
            html.append("<p style='color:").append(accentColor).append(";margin:6px 0 0 0;font-size:20px;font-weight:600;'>");
            html.append(arrow).append(" ").append(diffPercentage).append("% ").append(verb);
            html.append(" <span style='color:").append(FG_MUTED).append(";font-size:13px;font-weight:400;'>($").append(fmt(monthDiff.abs())).append(")</span>");
            html.append("</p>");
            html.append("</div></div>");
        }

        // Category breakdown — card with table layout and color dots
        html.append("<div style='padding:0 24px;margin-bottom:16px;'>");
        html.append("<div style='background-color:").append(BG_CARD).append(";border:1px solid ").append(BORDER).append(";border-radius:").append(RADIUS).append(";overflow:hidden;'>");
        // Card header
        html.append("<div style='padding:20px 20px 4px 20px;'>");
        html.append("<h2 style='margin:0;font-size:18px;font-weight:600;color:").append(FG).append(";'>Category Breakdown</h2>");
        html.append("<p style='margin:4px 0 0 0;font-size:13px;color:").append(FG_MUTED).append(";'>Spending distribution by category</p>");
        html.append("</div>");
        // Card content
        html.append("<div style='padding:12px 20px 20px 20px;'>");

        if (current.getCategoryBreakdown() != null && !current.getCategoryBreakdown().isEmpty()) {
            List<Map.Entry<String, CategoryBreakdown>> sorted = current.getCategoryBreakdown()
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, CategoryBreakdown>comparingByValue(
                            Comparator.comparing(CategoryBreakdown::getTotal)).reversed())
                    .toList();

            // Table header
            html.append("<table width='100%' cellpadding='0' cellspacing='0'>");
            html.append("<tr style='border-bottom:1px solid ").append(BORDER).append(";'>");
            html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";'>Category</td>");
            html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";text-align:right;'>Total</td>");
            html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";text-align:right;width:50px;'>Count</td>");
            html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";text-align:right;width:50px;'>%</td>");
            html.append("</tr>");

            for (Map.Entry<String, CategoryBreakdown> entry : sorted) {
                html.append(categoryRow(entry.getKey(), entry.getValue()));
            }
            html.append("</table>");
        } else {
            html.append("<p style='color:").append(FG_MUTED).append(";margin:0;font-size:13px;'>No expenses recorded this month.</p>");
        }
        html.append("</div>");
        html.append("</div></div>");

        // Top merchants — card with ranked list
        html.append("<div style='padding:0 24px;margin-bottom:16px;'>");
        html.append("<div style='background-color:").append(BG_CARD).append(";border:1px solid ").append(BORDER).append(";border-radius:").append(RADIUS).append(";overflow:hidden;'>");
        // Card header
        html.append("<div style='padding:20px 20px 4px 20px;'>");
        html.append("<h2 style='margin:0;font-size:18px;font-weight:600;color:").append(FG).append(";'>Top Merchants</h2>");
        html.append("<p style='margin:4px 0 0 0;font-size:13px;color:").append(FG_MUTED).append(";'>Most frequent and highest spending merchants</p>");
        html.append("</div>");
        // Card content
        html.append("<div style='padding:8px 20px 20px 20px;'>");

        if (current.getTopMerchants() != null && !current.getTopMerchants().isEmpty()) {
            for (int i = 0; i < current.getTopMerchants().size(); i++) {
                MerchantSummary ms = current.getTopMerchants().get(i);
                html.append(merchantRow(i + 1, ms));
            }
        } else {
            html.append("<p style='color:").append(FG_MUTED).append(";margin:0;font-size:13px;'>No merchants this month.</p>");
        }
        html.append("</div>");
        html.append("</div></div>");

        // Footer
        html.append("<div style='padding:8px 24px 24px 24px;text-align:center;'>");
        html.append("<p style='color:").append(FG_MUTED).append(";margin:0;font-size:12px;'>Spendifi \u2014 Automated Monthly Report</p>");
        html.append("</div>");

        html.append("</div>"); // end container
        html.append("</body></html>");

        return html.toString();
    }

    private String statCardTd(String label, String value, String footnote) {
        return "<td style='background-color:" + BG_CARD + ";border:1px solid " + BORDER + ";border-radius:" + RADIUS + ";padding:16px;vertical-align:top;' width='33%'>"
                + "<p style='color:" + FG_MUTED + ";margin:0;font-size:13px;'>" + esc(label) + "</p>"
                + "<p style='color:" + FG + ";margin:6px 0 0 0;font-size:20px;font-weight:600;letter-spacing:-0.02em;'>" + esc(value) + "</p>"
                + "<p style='color:" + FG_MUTED + ";margin:8px 0 0 0;font-size:11px;'>" + esc(footnote) + "</p>"
                + "</td>";
    }

    private String categoryRow(String name, CategoryBreakdown cb) {
        String color = CATEGORY_COLORS.getOrDefault(name, DEFAULT_CATEGORY_COLOR);
        return "<tr style='border-bottom:1px solid " + BORDER + ";'>"
                + "<td style='padding:10px 0;'>"
                + "<table cellpadding='0' cellspacing='0'><tr>"
                + "<td style='width:10px;height:10px;background-color:" + color + ";border-radius:50%;'></td>"
                + "<td style='padding-left:8px;font-size:13px;font-weight:500;color:" + FG + ";'>" + esc(name) + "</td>"
                + "</tr></table>"
                + "</td>"
                + "<td style='padding:10px 0;font-size:13px;font-weight:500;color:" + FG + ";text-align:right;'>$" + fmt(cb.getTotal()) + "</td>"
                + "<td style='padding:10px 0;font-size:13px;color:" + FG_MUTED + ";text-align:right;'>" + cb.getCount() + "</td>"
                + "<td style='padding:10px 0;font-size:13px;color:" + FG_MUTED + ";text-align:right;'>" + cb.getPercentage() + "%</td>"
                + "</tr>";
    }

    private String merchantRow(int rank, MerchantSummary ms) {
        String border = rank > 1 ? "border-top:1px solid " + BORDER + ";" : "";
        return "<table width='100%' cellpadding='0' cellspacing='0' style='padding:10px 0;" + border + "'><tr>"
                + "<td width='32' style='color:" + FG_MUTED + ";font-size:13px;font-weight:500;vertical-align:top;'>" + rank + ".</td>"
                + "<td style='vertical-align:top;'>"
                + "<p style='color:" + FG + ";margin:0;font-size:13px;font-weight:500;'>" + esc(ms.getMerchant()) + "</p>"
                + "<p style='color:" + FG_MUTED + ";margin:2px 0 0 0;font-size:11px;'>" + ms.getCount() + " transaction" + (ms.getCount() != 1 ? "s" : "") + "</p>"
                + "</td>"
                + "<td style='color:" + FG + ";font-size:13px;font-weight:500;text-align:right;vertical-align:top;'>$" + fmt(ms.getTotal()) + "</td>"
                + "</tr></table>";
    }

    private String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toString() : "0.00";
    }

    private String esc(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
