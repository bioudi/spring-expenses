package com.expensetracker.service;

import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.entity.RecurringExpense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringExpenseReminderService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final EmailService emailService;

    @Value("${insights.email.enabled:true}")
    private boolean emailEnabled;

    // Design tokens matching the app's light theme (same as MonthlyInsightsService)
    private static final String BG_PAGE = "#f5f5f5";
    private static final String BG_CARD = "#ffffff";
    private static final String FG = "#0f0f0f";
    private static final String FG_MUTED = "#636363";
    private static final String BORDER = "#e5e5e5";
    private static final String FONT_STACK = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif";
    private static final String RADIUS = "10px";
    private static final String ACCENT = "#2563eb";

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

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDailyRecurringReminders() {
        if (!emailEnabled) {
            log.info("Recurring expense reminder email is disabled. Skipping.");
            return;
        }

        LocalDate today = LocalDate.now();
        List<RecurringExpense> dueToday = recurringExpenseRepository.findDueOnDate(today);

        if (dueToday.isEmpty()) {
            log.info("No recurring expenses due today ({}). No reminders to send.", today);
            return;
        }

        // Group by user
        Map<User, List<RecurringExpense>> byUser = dueToday.stream()
                .collect(Collectors.groupingBy(RecurringExpense::getUser));

        log.info("Sending recurring expense reminders to {} users for {}", byUser.size(), today);

        String dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));

        for (Map.Entry<User, List<RecurringExpense>> entry : byUser.entrySet()) {
            User user = entry.getKey();
            List<RecurringExpense> expenses = entry.getValue();

            try {
                String subject = "Recurring Expenses Due Today \u2014 " + today.format(DateTimeFormatter.ofPattern("MMM d"));
                String htmlBody = buildEmailHtml(expenses, dateLabel);

                emailService.sendHtmlEmail(new String[]{user.getEmail()}, subject, htmlBody);
                log.info("Recurring expense reminder sent to '{}': {} expense(s) due", user.getEmail(), expenses.size());
            } catch (Exception e) {
                log.error("Failed to send recurring expense reminder to '{}': {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }

    private String buildEmailHtml(List<RecurringExpense> expenses, String dateLabel) {
        StringBuilder html = new StringBuilder();

        BigDecimal totalDue = expenses.stream()
                .map(RecurringExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Start HTML
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("</head>");
        html.append("<body style='margin:0;padding:0;background-color:").append(BG_PAGE).append(";font-family:").append(FONT_STACK).append(";'>");

        // Container
        html.append("<div style='max-width:600px;margin:24px auto;'>");

        // Header — same brand bar as monthly insights
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
        html.append("<h1 style='margin:0;font-size:22px;font-weight:700;color:").append(FG).append(";'>Recurring Expenses Due Today</h1>");
        html.append("<p style='margin:4px 0 0 0;font-size:14px;color:").append(FG_MUTED).append(";'>").append(esc(dateLabel)).append("</p>");
        html.append("</div>");

        // Summary stat cards
        html.append("<div style='padding:16px 24px 0 24px;'>");
        html.append("<table width='100%' cellpadding='0' cellspacing='0' style='margin-bottom:12px;'><tr>");
        html.append(statCardTd("Total Due", "$" + fmt(totalDue), expenses.size() + " recurring expense" + (expenses.size() != 1 ? "s" : "")));
        html.append("<td width='12'></td>");
        html.append(statCardTd("Expenses Today", String.valueOf(expenses.size()), "scheduled for today"));
        html.append("</tr></table>");
        html.append("</div>");

        // Expense list card
        html.append("<div style='padding:0 24px;margin-bottom:16px;'>");
        html.append("<div style='background-color:").append(BG_CARD).append(";border:1px solid ").append(BORDER).append(";border-radius:").append(RADIUS).append(";overflow:hidden;'>");

        // Card header
        html.append("<div style='padding:20px 20px 4px 20px;'>");
        html.append("<h2 style='margin:0;font-size:18px;font-weight:600;color:").append(FG).append(";'>Today's Expenses</h2>");
        html.append("<p style='margin:4px 0 0 0;font-size:13px;color:").append(FG_MUTED).append(";'>These recurring expenses are scheduled for today</p>");
        html.append("</div>");

        // Table header
        html.append("<div style='padding:12px 20px 20px 20px;'>");
        html.append("<table width='100%' cellpadding='0' cellspacing='0'>");
        html.append("<tr style='border-bottom:1px solid ").append(BORDER).append(";'>");
        html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";'>Merchant</td>");
        html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";'>Category</td>");
        html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";'>Frequency</td>");
        html.append("<td style='padding:8px 0;font-size:12px;font-weight:500;color:").append(FG_MUTED).append(";text-align:right;'>Amount</td>");
        html.append("</tr>");

        // Expense rows
        for (RecurringExpense expense : expenses) {
            html.append(expenseRow(expense));
        }

        // Total row
        html.append("<tr>");
        html.append("<td colspan='3' style='padding:12px 0 8px 0;font-size:14px;font-weight:600;color:").append(FG).append(";'>Total</td>");
        html.append("<td style='padding:12px 0 8px 0;font-size:14px;font-weight:600;color:").append(FG).append(";text-align:right;'>$").append(fmt(totalDue)).append("</td>");
        html.append("</tr>");

        html.append("</table>");
        html.append("</div>");
        html.append("</div></div>");

        // Footer
        html.append("<div style='padding:8px 24px 24px 24px;text-align:center;'>");
        html.append("<p style='color:").append(FG_MUTED).append(";margin:0;font-size:12px;'>Spendifi \u2014 Daily Recurring Expense Reminder</p>");
        html.append("</div>");

        html.append("</div>"); // end container
        html.append("</body></html>");

        return html.toString();
    }

    private String statCardTd(String label, String value, String footnote) {
        return "<td style='background-color:" + BG_CARD + ";border:1px solid " + BORDER + ";border-radius:" + RADIUS + ";padding:16px;vertical-align:top;' width='50%'>"
                + "<p style='color:" + FG_MUTED + ";margin:0;font-size:13px;'>" + esc(label) + "</p>"
                + "<p style='color:" + FG + ";margin:6px 0 0 0;font-size:20px;font-weight:600;letter-spacing:-0.02em;'>" + esc(value) + "</p>"
                + "<p style='color:" + FG_MUTED + ";margin:8px 0 0 0;font-size:11px;'>" + esc(footnote) + "</p>"
                + "</td>";
    }

    private String expenseRow(RecurringExpense expense) {
        String color = CATEGORY_COLORS.getOrDefault(expense.getCategory(), DEFAULT_CATEGORY_COLOR);
        return "<tr style='border-bottom:1px solid " + BORDER + ";'>"
                + "<td style='padding:10px 0;font-size:13px;font-weight:500;color:" + FG + ";'>" + esc(expense.getMerchant()) + "</td>"
                + "<td style='padding:10px 0;'>"
                + "<table cellpadding='0' cellspacing='0'><tr>"
                + "<td style='width:10px;height:10px;background-color:" + color + ";border-radius:50%;'></td>"
                + "<td style='padding-left:8px;font-size:13px;color:" + FG + ";'>" + esc(expense.getCategory()) + "</td>"
                + "</tr></table>"
                + "</td>"
                + "<td style='padding:10px 0;font-size:13px;color:" + FG_MUTED + ";'>" + formatFrequency(expense) + "</td>"
                + "<td style='padding:10px 0;font-size:13px;font-weight:500;color:" + FG + ";text-align:right;'>$" + fmt(expense.getAmount()) + "</td>"
                + "</tr>";
    }

    private String formatFrequency(RecurringExpense expense) {
        RecurrenceFrequency freq = expense.getFrequency();
        switch (freq) {
            case DAILY:
                return "Daily";
            case WEEKLY:
                if (expense.getDayOfWeek() != null) {
                    return "Every " + capitalize(expense.getDayOfWeek().name());
                }
                return "Weekly";
            case BI_WEEKLY:
                if (expense.getDayOfWeek() != null) {
                    return "Every other " + capitalize(expense.getDayOfWeek().name());
                }
                return "Bi-weekly";
            case MONTHLY:
                if (expense.getDayOfMonth() != null) {
                    return "Monthly (" + ordinal(expense.getDayOfMonth()) + ")";
                }
                return "Monthly";
            default:
                return freq.name();
        }
    }

    private String capitalize(String dayName) {
        return dayName.charAt(0) + dayName.substring(1).toLowerCase();
    }

    private String ordinal(int n) {
        String[] suffixes = {"th", "st", "nd", "rd"};
        int v = n % 100;
        String suffix = (v >= 11 && v <= 13) ? "th" : suffixes[Math.min(n % 10, 3)];
        return n + suffix;
    }

    private String fmt(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP).toString() : "0.00";
    }

    private String esc(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
