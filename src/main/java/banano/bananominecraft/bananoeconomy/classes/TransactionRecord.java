package banano.bananominecraft.bananoeconomy.classes;

import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.helpers.StringHelper;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record TransactionRecord(
        LocalDateTime transactionDate,
        String address,
        TransactionDirection direction,
        double amount,
        String transactionHash,
        boolean confirmed
)
{
    /** Compact, fixed-width date format: "dd/MM/yy HH:mm" (14 chars). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    // Column widths (excluding the single space separator between each column)
    private static final int COL_DATE   = 14; // "dd/MM/yy HH:mm"
    private static final int COL_AMOUNT = 10; // right-aligned amount
    private static final int COL_CONF   =  3; // "Y/N"
    private static final int COL_HASH   = 11; // "xxxxxxxx..."

    public BaseComponent[] toRecordString(ConfigEngine configEngine)
    {
        final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
        final StringBuilder sb = new StringBuilder();

        sb.append(direction == TransactionDirection.Send ? ChatColor.YELLOW : ChatColor.GREEN);
        sb.append(StringHelper.padRight(transactionDate.format(DATE_FMT), COL_DATE));
        sb.append(" ");
        sb.append(StringHelper.padLeft(df.format(amount), COL_AMOUNT));
        sb.append(" ");
        sb.append(StringHelper.padRight(confirmed ? "Y" : "N", COL_CONF));
        sb.append(" ");

        ComponentBuilder componentBuilder = new ComponentBuilder(sb.toString());

        componentBuilder.append(MessageGenerator.generateBlockExplorerLink(configEngine, transactionHash, transactionHash.substring(0, 8)));
        componentBuilder.append("...");

        return componentBuilder.create();
    }

    public static String getHeaderString()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(StringHelper.padRight("Date",        COL_DATE   + 1));
        sb.append(StringHelper.padRight("Amount",      COL_AMOUNT + 1));
        sb.append(StringHelper.padRight("C",           COL_CONF   + 1));
        sb.append("Hash");

        return sb.toString();
    }
}
