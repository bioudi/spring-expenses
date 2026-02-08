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
        html.append("<body style='margin:0;padding:0;background-color:#0f1117;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;'>");

        // Container
        html.append("<div style='max-width:600px;margin:20px auto;background-color:#1c1f26;border-radius:12px;overflow:hidden;'>");

        // Header
        html.append("<div style='background:linear-gradient(135deg,#58a6ff 0%,#6366f1 100%);padding:32px;text-align:center;'>");
        html.append("<h1 style='color:#ffffff;margin:0;font-size:24px;font-weight:700;'>Monthly Expense Insights</h1>");
        html.append("<p style='color:#c8d6e5;margin:8px 0 0 0;font-size:15px;'>").append(esc(monthName)).append("</p>");
        html.append("</div>");

        // Content
        html.append("<div style='padding:24px;'>");

        // Stats row 1: Total Spent, Transactions, Avg/Tx
        html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:16px;'><tr>");
        html.append(statCardTd("Total Spent", "$" + fmt(totalSpent), "#58a6ff"));
        html.append("<td width='8'></td>");
        html.append(statCardTd("Transactions", String.valueOf(txCount), "#6366f1"));
        html.append("<td width='8'></td>");
        html.append(statCardTd("Avg / Transaction", "$" + fmt(avgPerTx), "#8b5cf6"));
        html.append("</tr></table>");

        // Stats row 2: Avg Daily, Highest Day
        html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:20px;'><tr>");
        html.append(statCardTd("Avg Daily", "$" + fmt(avgDaily), "#06b6d4"));
        html.append("<td width='8'></td>");
        if (highestDay != null) {
            String dayLabel = highestDay.getDate().format(DateTimeFormatter.ofPattern("MMM d"));
            html.append(statCardTd("Highest Day", "$" + fmt(highestDay.getTotal()) + " (" + dayLabel + ")", "#f97316"));
        } else {
            html.append(statCardTd("Highest Day", "$0.00", "#f97316"));
        }
        html.append("</tr></table>");

        // Previous month comparison
        if (prevTotal.compareTo(BigDecimal.ZERO) > 0) {
            String arrow = spentMore ? "&#9650;" : "&#9660;";
            String color = spentMore ? "#f97316" : "#10b981";
            String verb = spentMore ? "more" : "less";
            html.append("<div style='background-color:#252830;border-radius:8px;padding:16px;margin-bottom:24px;border-left:4px solid ").append(color).append(";'>");
            html.append("<p style='color:#8b949e;margin:0;font-size:13px;'>vs. Previous Month</p>");
            html.append("<p style='color:").append(color).append(";margin:6px 0 0 0;font-size:20px;font-weight:700;'>");
            html.append(arrow).append(" ").append(diffPercentage).append("% ").append(verb);
            html.append(" <span style='color:#8b949e;font-size:13px;font-weight:400;'>($").append(fmt(monthDiff.abs())).append(")</span>");
            html.append("</p></div>");
        }

        // Category breakdown
        html.append("<h2 style='color:#e1e4e8;font-size:16px;font-weight:600;margin:0 0 12px 0;'>Spending by Category</h2>");
        html.append("<div style='background-color:#252830;border-radius:8px;padding:16px;margin-bottom:24px;'>");

        if (current.getCategoryBreakdown() != null && !current.getCategoryBreakdown().isEmpty()) {
            List<Map.Entry<String, CategoryBreakdown>> sorted = current.getCategoryBreakdown()
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, CategoryBreakdown>comparingByValue(
                            Comparator.comparing(CategoryBreakdown::getTotal)).reversed())
                    .toList();

            for (Map.Entry<String, CategoryBreakdown> entry : sorted) {
                html.append(categoryRow(entry.getKey(), entry.getValue()));
            }
        } else {
            html.append("<p style='color:#8b949e;margin:0;'>No expenses recorded this month.</p>");
        }
        html.append("</div>");

        // Top merchants
        html.append("<h2 style='color:#e1e4e8;font-size:16px;font-weight:600;margin:0 0 12px 0;'>Top Merchants</h2>");
        html.append("<div style='background-color:#252830;border-radius:8px;padding:16px;'>");

        if (current.getTopMerchants() != null && !current.getTopMerchants().isEmpty()) {
            for (int i = 0; i < current.getTopMerchants().size(); i++) {
                MerchantSummary ms = current.getTopMerchants().get(i);
                html.append(merchantRow(i + 1, ms));
            }
        } else {
            html.append("<p style='color:#8b949e;margin:0;'>No merchants this month.</p>");
        }
        html.append("</div>");

        html.append("</div>"); // end content padding

        // Footer
        html.append("<div style='background-color:#0f1117;padding:20px;text-align:center;'>");
        html.append("<p style='color:#484f58;margin:0;font-size:12px;'>Spendifi \u2014 Automated Monthly Report</p>");
        html.append("</div>");

        html.append("</div>"); // end container
        html.append("</body></html>");

        return html.toString();
    }

    private String statCardTd(String label, String value, String color) {
        return "<td style='background-color:#252830;border-radius:8px;padding:16px;text-align:center;' width='33%'>"
                + "<p style='color:#8b949e;margin:0;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;'>" + esc(label) + "</p>"
                + "<p style='color:" + color + ";margin:6px 0 0 0;font-size:18px;font-weight:700;'>" + esc(value) + "</p>"
                + "</td>";
    }

    private String categoryRow(String name, CategoryBreakdown cb) {
        int barWidth = cb.getPercentage().intValue();
        if (barWidth < 1) barWidth = 1;
        if (barWidth > 100) barWidth = 100;
        return "<div style='margin-bottom:14px;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0'><tr>"
                + "<td style='color:#e1e4e8;font-size:13px;'>" + esc(name) + " <span style='color:#484f58;'>(" + cb.getCount() + ")</span></td>"
                + "<td style='color:#8b949e;font-size:13px;text-align:right;'>$" + fmt(cb.getTotal()) + "</td>"
                + "</tr></table>"
                + "<div style='background-color:#1c1f26;border-radius:4px;height:6px;margin-top:6px;overflow:hidden;'>"
                + "<div style='background:linear-gradient(90deg,#58a6ff,#6366f1);height:100%;width:" + barWidth + "%;border-radius:4px;'></div>"
                + "</div>"
                + "<p style='color:#484f58;font-size:11px;margin:2px 0 0 0;text-align:right;'>" + cb.getPercentage() + "%</p>"
                + "</div>";
    }

    private String merchantRow(int rank, MerchantSummary ms) {
        String border = rank > 1 ? "border-top:1px solid #1c1f26;" : "";
        return "<table width='100%' cellpadding='0' cellspacing='0' style='padding:10px 0;" + border + "'><tr>"
                + "<td width='30' style='color:#58a6ff;font-size:16px;font-weight:700;vertical-align:top;'>" + rank + "</td>"
                + "<td style='vertical-align:top;'>"
                + "<p style='color:#e1e4e8;margin:0;font-size:13px;font-weight:500;'>" + esc(ms.getMerchant()) + "</p>"
                + "<p style='color:#484f58;margin:2px 0 0 0;font-size:11px;'>" + ms.getCount() + " transaction" + (ms.getCount() != 1 ? "s" : "") + "</p>"
                + "</td>"
                + "<td style='color:#8b949e;font-size:14px;font-weight:600;text-align:right;vertical-align:top;'>$" + fmt(ms.getTotal()) + "</td>"
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
