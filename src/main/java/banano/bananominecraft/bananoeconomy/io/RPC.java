package banano.bananominecraft.bananoeconomy.io;

import banano.bananominecraft.bananoeconomy.validation.Validator;
import banano.bananominecraft.bananoeconomy.classes.TransactionRecord;
import banano.bananominecraft.bananoeconomy.configuration.ConfigEngine;
import banano.bananominecraft.bananoeconomy.enums.TransactionDirection;
import banano.bananominecraft.bananoeconomy.exceptions.TransactionError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

/**
 * Communicates with the Banano node over HTTP/JSON-RPC.
 *
 * <p>All configuration values (node URL, wallet ID, master wallet, multiplier)
 * are read from {@link ConfigEngine} rather than directly from the Bukkit config
 * file, so that admin changes made at runtime are always reflected immediately
 * without requiring a plugin reload.</p>
 */
public class RPC
{
    /**
     * Sentinel returned by {@link #accountCreate} when the node call fails.
     * Callers should compare against this constant rather than a bare string literal.
     */
    public static final String ACCOUNT_CREATION_FAILED = "Account Creation Failed";

    private final Plugin plugin;
    private final ConfigEngine configEngine;

    public RPC(Plugin plugin, ConfigEngine configEngine)
    {
        this.plugin = plugin;
        this.configEngine = configEngine;
    }

    public URL getURL() throws Exception
    {
        return new URL(configEngine.getNodeAddress());
    }

    private BigDecimal getMultiplier()
    {
        return new BigDecimal(configEngine.getMultiplier());
    }

    private String getWalletID()
    {
        return configEngine.getWalletId();
    }

    public String getMasterWallet()
    {
        return configEngine.getMasterWallet();
    }

    // -------------------------------------------------------------------------
    // HTTP transport
    // -------------------------------------------------------------------------

    public String sendPost(String payload) throws Exception
    {
        URL url = getURL();

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        // Write request — let the exception propagate so callers don't attempt
        // to read a response that will never arrive.
        try (OutputStream os = con.getOutputStream())
        {
            byte[] input = payload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)))
        {
            String responseLine;
            while ((responseLine = br.readLine()) != null)
            {
                response.append(responseLine.trim());
            }
        }

        return response.toString();
    }

    // -------------------------------------------------------------------------
    // Account / wallet operations
    // -------------------------------------------------------------------------

    public String accountCreate(int index)
    {
        JsonObject json_payload = new JsonObject();
        String wallID = getWalletID();

        json_payload.addProperty("action", "account_create");
        json_payload.addProperty("wallet", wallID);

        if (index != -1)
        {
            json_payload.addProperty("index", index);
        }

        try
        {
            String accountResponse = sendPost(json_payload.toString());
            JsonElement accountJson = JsonParser.parseString(accountResponse);
            return accountJson.getAsJsonObject().get("account").getAsString();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Account creation failed.", e);
        }
        return ACCOUNT_CREATION_FAILED;
    }

    private BigInteger toRaw(double value)
    {
        BigDecimal multiplier = getMultiplier();
        BigDecimal bValue = new BigDecimal(Double.toString(value));
        BigDecimal raw = bValue.multiply(multiplier);
        return raw.toBigInteger();
    }

    /**
     * Convert a raw Banano amount (as BigDecimal) to the human-readable BAN value.
     * Public so that other classes (e.g. BananoWebSocket) can reuse the conversion.
     */
    public Double fromRaw(BigDecimal bigDecimal)
    {
        BigDecimal divisor = getMultiplier();
        BigDecimal result = bigDecimal.divide(divisor, 10, RoundingMode.HALF_UP);
        return result.doubleValue();
    }

    public String sendTransaction(String sender, String recipient, double value) throws TransactionError
    {
        if (value <= 0)
        {
            throw new TransactionError("Transaction amount must be greater than zero.");
        }

        if (!Validator.validateAddress(sender) || !Validator.validateAddress(recipient))
        {
            throw new TransactionError("Invalid address for sender or recipient.");
        }

        final JsonObject json_payload = new JsonObject();
        final String walletID = getWalletID();

        json_payload.addProperty("action", "send");
        json_payload.addProperty("wallet", walletID);
        json_payload.addProperty("source", sender);
        json_payload.addProperty("destination", recipient);
        json_payload.addProperty("amount", toRaw(value));

        final JsonElement accountJson;

        try
        {
            final String sendResponse = sendPost(json_payload.toString());
            accountJson = JsonParser.parseString(sendResponse);
        }
        catch (final Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to sendTransaction.", e);
            throw new TransactionError("Send transaction failed");
        }

        final JsonObject json = accountJson.getAsJsonObject();
        final JsonElement error = json.get("error");
        if (error != null)
        {
            throw new TransactionError(error.getAsString());
        }

        return Optional
                .ofNullable(json.get("block"))
                .map(JsonElement::getAsString)
                .orElseThrow(() -> new TransactionError("Send transaction resulted in missing block"));
    }

    public Double getBalance(String account)
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action", "account_info");
        json_payload.addProperty("account", account);

        try
        {
            String balanceResponse = sendPost(json_payload.toString());
            JsonElement accountJson = JsonParser.parseString(balanceResponse);
            try
            {
                BigDecimal bigDecimal = accountJson.getAsJsonObject().get("balance").getAsBigDecimal();
                return fromRaw(bigDecimal);
            }
            catch (Exception e)
            {
                JsonElement errorElement = accountJson.getAsJsonObject().get("error");
                if (errorElement != null && errorElement.getAsString().equals("Account not found"))
                {
                    return 0.0;
                }
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve balance for account: " + account, e);
        }
        return 0.0;
    }

    public List<TransactionRecord> getTransactionHistory(String account, int numberOfRecords)
    {
        final JsonObject json_payload = new JsonObject();

        json_payload.addProperty("action", "account_history");
        json_payload.addProperty("account", account);
        json_payload.addProperty("count", numberOfRecords);

        try
        {
            String transactionHistoryResponse = sendPost(json_payload.toString());
            JsonElement transactionHistoryJson = JsonParser.parseString(transactionHistoryResponse);

            try
            {
                List<TransactionRecord> records = new ArrayList<TransactionRecord>();

                if (transactionHistoryJson.getAsJsonObject().has("history")
                      && transactionHistoryJson.getAsJsonObject().get("history").isJsonArray())
                {
                    for (JsonElement arrayItem : transactionHistoryJson.getAsJsonObject().get("history").getAsJsonArray())
                    {
                        JsonObject recordJson = arrayItem.getAsJsonObject();

                        if (recordJson.has("type"))
                        {
                            TransactionDirection direction = TransactionDirection.Send;

                            if (recordJson.get("type").getAsString().equals("send"))
                            {
                                direction = TransactionDirection.Send;
                            }
                            else if (recordJson.get("type").getAsString().equals("receive"))
                            {
                                direction = TransactionDirection.Receive;
                            }

                            String address = recordJson.get("account").getAsString();
                            double amount = fromRaw(recordJson.get("amount").getAsBigDecimal());
                            LocalDateTime timestamp = LocalDateTime.ofInstant(Instant.ofEpochSecond(recordJson.get("local_timestamp").getAsLong()),
                                                                                TimeZone.getDefault().toZoneId());
                            String hash = recordJson.get("hash").getAsString();
                            boolean confirmed = false;

                            if (recordJson.has("confirmed"))
                            {
                                confirmed = recordJson.get("confirmed").getAsBoolean();
                            }

                            TransactionRecord record = new TransactionRecord(
                                    timestamp,
                                    address,
                                    direction,
                                    amount,
                                    hash,
                                    confirmed
                            );

                            records.add(record);
                        }
                    }
                }

                return records;
            }
            catch (Exception e)
            {
                plugin.getLogger().log(Level.WARNING, "Failed to retrieve history for account: " + account, e);

                JsonElement errorElement = transactionHistoryJson.getAsJsonObject().get("error");
                if (errorElement != null && errorElement.getAsString().equals("Account not found"))
                {
                    return null;
                }
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve transaction history for account: " + account, e);
        }

        return null;
    }

    public List<String> getBlockCount()
    {
        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "block_count");

        try
        {
            JsonElement blocksJson = JsonParser.parseString(sendPost(json_payload.toString()));
            String checked   = blocksJson.getAsJsonObject().get("count").getAsString();
            String unchecked = blocksJson.getAsJsonObject().get("unchecked").getAsString();
            return Arrays.asList(checked, unchecked);
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve block count.", e);
        }
        return Arrays.asList("Null", "Null");
    }

    public Boolean wallet_exists()
    {
        final JsonObject json_payload = new JsonObject();
        final String walletID = getWalletID();

        json_payload.addProperty("action", "wallet_balances");
        json_payload.addProperty("wallet", walletID);

        try
        {
            JsonElement existsJson = JsonParser.parseString(sendPost(json_payload.toString()));
            try
            {
                String exists = existsJson.getAsJsonObject().get("error").getAsString();
                if (exists.equals("Wallet not found") || exists.equals("Bad wallet number"))
                {
                    return false;
                }
                else
                {
                    plugin.getLogger().warning("Unexpected wallet_balances error: " + exists);
                }
            }
            catch (Exception e)
            {
                plugin.getLogger().info("Master wallet found.");
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check wallet existence.", e);
        }
        return true;
    }

    public Boolean wallet_contains(String account)
    {
        final JsonObject json_payload = new JsonObject();
        final String walletID = getWalletID();

        json_payload.addProperty("action", "account_contains");
        json_payload.addProperty("wallet", walletID);
        json_payload.addProperty("account", account);

        try
        {
            JsonElement existsJson = JsonParser.parseString(sendPost(json_payload.toString()));
            int exists = existsJson.getAsJsonObject().get("exists").getAsInt();

            if (exists == 1)
            {
                plugin.getLogger().info("Account is in wallet: " + account);
                return true;
            }
            else if (exists == 0)
            {
                return false;
            }
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to check wallet_contains for account: " + account, e);
        }
        return false;
    }

    public void walletCreate()
    {
        String seed = configEngine.getWalletSeed();

        final JsonObject json_payload = new JsonObject();
        json_payload.addProperty("action", "wallet_create");
        json_payload.addProperty("seed", seed);

        try
        {
            JsonElement blocksJson = JsonParser.parseString(sendPost(json_payload.toString()));
            String walletID = blocksJson.getAsJsonObject().get("wallet").getAsString();

            plugin.getLogger().info("Wallet ID generated: " + walletID);

            // Persist through ConfigEngine so save() writes all fields consistently.
            configEngine.setWalletId(walletID);
            configEngine.save();
        }
        catch (Exception e)
        {
            plugin.getLogger().log(Level.SEVERE, "walletCreate failed.", e);
        }
    }
}
