package com.anil.service;

import com.anil.model.ChatMessage;
import com.anil.model.CoinChatMessage;
import com.anil.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Value("${coingecko.api.key}")
    private String coingeckoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String COINGECKO_API_BASE = "https://api.coingecko.com/api/v3";

    // Keywords mapping for different types of queries
    private final Map<String, List<String>> queryKeywords = new HashMap<>() {{
        put("price", Arrays.asList("price", "cost", "worth", "value", "rate", "trading at", "current price"));
        put("trend", Arrays.asList("trend", "moving", "performance", "performing", "going", "market", "direction"));
        put("top", Arrays.asList("top", "best", "highest", "leading", "biggest", "largest", "most"));
        put("gain", Arrays.asList("gain", "increase", "up", "risen", "growth", "growing", "profit"));
        put("loss", Arrays.asList("loss", "decrease", "down", "fallen", "dropping", "dip", "crash"));
        put("volume", Arrays.asList("volume", "trading volume", "liquidity", "traded", "exchange volume"));
        put("market_cap", Arrays.asList("market cap", "capitalization", "market value", "valuation"));
        put("wallet", Arrays.asList("wallet", "store", "hold", "keep", "storage", "save"));
        put("invest", Arrays.asList("invest", "buy", "purchase", "trade", "trading", "investment"));
        put("coin", Arrays.asList("bitcoin", "btc", "eth", "ethereum", "usdt", "bnb", "xrp", "ada", "doge"));
    }};

    public ChatMessage processUserMessage(String content, String sender) {
        // Save user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setContent(content);
        userMessage.setSender(sender);
        userMessage.setMessageType("USER");
        chatMessageRepository.save(userMessage);

        // Generate and save bot response
        ChatMessage botResponse = generateBotResponse(content);
        botResponse.setSender(sender);
        return chatMessageRepository.save(botResponse);
    }

    public List<ChatMessage> getChatHistory(String sender) {
        return chatMessageRepository.findBySenderOrderByCreatedAtAsc(sender);
    }

    private ChatMessage generateBotResponse(String userMessage) {
        ChatMessage response = new ChatMessage();
        response.setMessageType("BOT");
        
        // Simple response logic - can be enhanced with more sophisticated AI/ML
        String lowerCaseMessage = userMessage.toLowerCase();
        
        if (lowerCaseMessage.contains("hello") || lowerCaseMessage.contains("hi")) {
            response.setContent("Hello! How can I help you today?");
        } else if (lowerCaseMessage.contains("price") || lowerCaseMessage.contains("cost")) {
            response.setContent("I can help you with price information. What specific item are you interested in?");
        } else if (lowerCaseMessage.contains("help")) {
            response.setContent("I can help you with:\n1. Price information\n2. Order status\n3. Account issues\n4. Technical support\nWhat do you need help with?");
        } else if (lowerCaseMessage.contains("thank")) {
            response.setContent("You're welcome! Is there anything else I can help you with?");
        } else {
            response.setContent("I'm not sure I understand. Could you please rephrase your question?");
        }
        
        return response;
    }
    
    public CoinChatMessage processCoinQuery(String prompt) {
        CoinChatMessage response = new CoinChatMessage();
        String lowerCasePrompt = prompt.toLowerCase();
        Set<String> detectedCategories = new HashSet<>();

        // Detect query categories based on keywords
        for (Map.Entry<String, List<String>> entry : queryKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerCasePrompt.contains(keyword.toLowerCase())) {
                    detectedCategories.add(entry.getKey());
                }
            }
        }

        try {
            // Generate response based on detected categories
            if (detectedCategories.isEmpty()) {
                response.setMessage(getGeneralCryptoInfo(prompt));
                return response;
            }

            StringBuilder result = new StringBuilder();

            // Handle price queries for specific coins
            if (detectedCategories.contains("price") && detectedCategories.contains("coin")) {
                for (String coinKeyword : queryKeywords.get("coin")) {
                    if (lowerCasePrompt.contains(coinKeyword)) {
                        result.append(getCoinPrice(mapCoinKeywordToId(coinKeyword))).append("\n\n");
                    }
                }
            }

            // Handle top gainers/losers
            if ((detectedCategories.contains("top") || detectedCategories.contains("best")) && 
                (detectedCategories.contains("gain") || detectedCategories.contains("loss"))) {
                result.append(getTopPerformers()).append("\n");
            }

            // Handle market trends
            if (detectedCategories.contains("trend")) {
                result.append(getMarketOverview()).append("\n");
            }

            // Handle volume queries
            if (detectedCategories.contains("volume")) {
                result.append(getTopVolumeCoins()).append("\n");
            }

            // Handle market cap queries
            if (detectedCategories.contains("market_cap")) {
                result.append(getTopMarketCap()).append("\n");
            }

            // Handle investment advice
            if (detectedCategories.contains("invest")) {
                result.append(getInvestmentAdvice()).append("\n");
            }

            // Handle wallet queries
            if (detectedCategories.contains("wallet")) {
                result.append(getWalletInfo()).append("\n");
            }

            // If no specific category matched but contains coin name, provide comprehensive info
            if (detectedCategories.contains("coin") && result.length() == 0) {
                for (String coinKeyword : queryKeywords.get("coin")) {
                    if (lowerCasePrompt.contains(coinKeyword)) {
                        result.append(getComprehensiveCoinInfo(mapCoinKeywordToId(coinKeyword))).append("\n\n");
                    }
                }
            }

            response.setMessage(result.length() > 0 ? result.toString() : getGeneralCryptoInfo(prompt));
            return response;

        } catch (Exception e) {
            response.setMessage("I apologize, but I'm having trouble fetching the latest cryptocurrency data. Please try again in a moment.");
            return response;
        }
    }

    private String getComprehensiveCoinInfo(String coinId) {
        try {
            String url = COINGECKO_API_BASE + "/coins/" + coinId + "?localization=false&tickers=false&community_data=false&developer_data=false";
            String response = restTemplate.getForObject(url, String.class);
            JSONObject coin = new JSONObject(response);

            JSONObject marketData = coin.getJSONObject("market_data");
            double currentPrice = marketData.getJSONObject("current_price").getDouble("usd");
            double priceChange24h = marketData.getDouble("price_change_percentage_24h");
            double marketCap = marketData.getJSONObject("market_cap").getDouble("usd");
            double volume = marketData.getJSONObject("total_volume").getDouble("usd");

            return String.format("%s (%s) Overview:\n\n" +
                "Current Price: $%,.2f\n" +
                "24h Change: %.2f%%\n" +
                "Market Cap: $%,.0f\n" +
                "24h Volume: $%,.0f\n" +
                "Description: %s",
                coin.getString("name"),
                coin.getString("symbol").toUpperCase(),
                currentPrice,
                priceChange24h,
                marketCap,
                volume,
                coin.getJSONObject("description").getString("en").split("\\.")[0] + ".");
        } catch (Exception e) {
            return "Unable to fetch detailed information for " + coinId + " at the moment.";
        }
    }

    private String mapCoinKeywordToId(String keyword) {
        Map<String, String> coinMapping = new HashMap<>() {{
            put("btc", "bitcoin");
            put("eth", "ethereum");
            put("usdt", "tether");
            put("bnb", "binance-coin");
            put("xrp", "ripple");
            put("ada", "cardano");
            put("doge", "dogecoin");
        }};
        return coinMapping.getOrDefault(keyword.toLowerCase(), keyword.toLowerCase());
    }

    private String getInvestmentAdvice() {
        return "Cryptocurrency Investment Guidelines:\n\n" +
               "1. Research & Due Diligence:\n" +
               "   - Study the project's whitepaper\n" +
               "   - Analyze the team and backers\n" +
               "   - Review historical performance\n\n" +
               "2. Risk Management:\n" +
               "   - Only invest what you can afford to lose\n" +
               "   - Diversify your portfolio\n" +
               "   - Use stop-loss orders\n\n" +
               "3. Security Best Practices:\n" +
               "   - Use secure wallets\n" +
               "   - Enable 2FA on all accounts\n" +
               "   - Keep private keys safe\n\n" +
               "4. Investment Strategies:\n" +
               "   - Consider dollar-cost averaging\n" +
               "   - Have a long-term perspective\n" +
               "   - Monitor market trends";
    }

    private String getWalletInfo() {
        return "Cryptocurrency Wallet Guide:\n\n" +
               "1. Hardware Wallets (Most Secure):\n" +
               "   - Ledger Nano X/S\n" +
               "   - Trezor Model T/One\n" +
               "   - KeepKey\n\n" +
               "2. Software Wallets:\n" +
               "   - MetaMask (ETH & ERC-20)\n" +
               "   - Trust Wallet (Multi-coin)\n" +
               "   - Exodus (Desktop)\n\n" +
               "3. Mobile Wallets:\n" +
               "   - Coinbase Wallet\n" +
               "   - Mycelium (Bitcoin)\n" +
               "   - Atomic Wallet\n\n" +
               "Security Tips:\n" +
               "- Always backup your seed phrase\n" +
               "- Use 2FA when available\n" +
               "- Never share private keys";
    }

    private String getTopPerformers() {
        String url = COINGECKO_API_BASE + "/coins/markets?vs_currency=usd&order=price_change_percentage_24h_desc&per_page=5&page=1&sparkline=false";
        String response = restTemplate.getForObject(url, String.class);
        
        JSONArray coins = new JSONArray(response);
        StringBuilder result = new StringBuilder("Top performing cryptocurrencies in the last 24 hours:\n\n");
        
        for (int i = 0; i < coins.length(); i++) {
            JSONObject coin = coins.getJSONObject(i);
            result.append(i + 1).append(". ")
                  .append(coin.getString("name"))
                  .append(" (").append(coin.getString("symbol").toUpperCase()).append(")\n")
                  .append("Price: $").append(String.format("%.2f", coin.getDouble("current_price"))).append("\n")
                  .append("24h Change: ").append(String.format("%.2f", coin.getDouble("price_change_percentage_24h"))).append("%\n\n");
        }
        
        return result.toString();
    }

    private String getCoinPrice(String coinId) {
        String url = COINGECKO_API_BASE + "/simple/price?ids=" + coinId + "&vs_currencies=usd&include_24hr_change=true&include_market_cap=true";
        String response = restTemplate.getForObject(url, String.class);
        
        JSONObject data = new JSONObject(response);
        JSONObject coinData = data.getJSONObject(coinId);
        
        return String.format("%s current price: $%,.2f\n24h Change: %.2f%%\nMarket Cap: $%,.2f",
            coinId.substring(0, 1).toUpperCase() + coinId.substring(1),
            coinData.getDouble("usd"),
            coinData.getDouble("usd_24h_change"),
            coinData.getDouble("usd_market_cap"));
    }

    private String getMarketOverview() {
        try {
            String url = COINGECKO_API_BASE + "/global";
            String response = restTemplate.getForObject(url, String.class);
            
            JSONObject data = new JSONObject(response).getJSONObject("data");
            JSONObject totalMarketCap = data.getJSONObject("total_market_cap");
            JSONObject totalVolume = data.getJSONObject("total_volume");
            JSONObject marketCapPercentage = data.getJSONObject("market_cap_percentage");
            
            double marketCapUsd = totalMarketCap.getDouble("usd");
            double volumeUsd = totalVolume.getDouble("usd");
            double btcDominance = marketCapPercentage.getDouble("btc");
            int activeCoins = data.getInt("active_cryptocurrencies");
            int markets = data.getInt("markets");
            
            return String.format("Crypto Market Overview:\n\n" +
                "Total Market Cap: $%,.0f\n" +
                "24h Volume: $%,.0f\n" +
                "Bitcoin Dominance: %.2f%%\n" +
                "Active Cryptocurrencies: %d\n" +
                "Markets: %d",
                marketCapUsd,
                volumeUsd,
                btcDominance,
                activeCoins,
                markets);
        } catch (Exception e) {
            return "Unable to fetch market overview at the moment. Please try again later.";
        }
    }

    private String getTopVolumeCoins() {
        String url = COINGECKO_API_BASE + "/coins/markets?vs_currency=usd&order=volume_desc&per_page=5&page=1&sparkline=false";
        String response = restTemplate.getForObject(url, String.class);
        
        JSONArray coins = new JSONArray(response);
        StringBuilder result = new StringBuilder("Top cryptocurrencies by 24h trading volume:\n\n");
        
        for (int i = 0; i < coins.length(); i++) {
            JSONObject coin = coins.getJSONObject(i);
            result.append(i + 1).append(". ")
                  .append(coin.getString("name"))
                  .append(" (").append(coin.getString("symbol").toUpperCase()).append(")\n")
                  .append("Volume: $").append(String.format("%,.0f", coin.getDouble("total_volume"))).append("\n")
                  .append("Price: $").append(String.format("%.2f", coin.getDouble("current_price"))).append("\n\n");
        }
        
        return result.toString();
    }

    private String getTopMarketCap() {
        String url = COINGECKO_API_BASE + "/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=5&page=1&sparkline=false";
        String response = restTemplate.getForObject(url, String.class);
        
        JSONArray coins = new JSONArray(response);
        StringBuilder result = new StringBuilder("Top cryptocurrencies by market capitalization:\n\n");
        
        for (int i = 0; i < coins.length(); i++) {
            JSONObject coin = coins.getJSONObject(i);
            result.append(i + 1).append(". ")
                  .append(coin.getString("name"))
                  .append(" (").append(coin.getString("symbol").toUpperCase()).append(")\n")
                  .append("Market Cap: $").append(String.format("%,.0f", coin.getDouble("market_cap"))).append("\n")
                  .append("Price: $").append(String.format("%.2f", coin.getDouble("current_price"))).append("\n\n");
        }
        
        return result.toString();
    }

    private String getGeneralCryptoInfo(String prompt) {
        if (prompt.toLowerCase().contains("wallet")) {
            return "Popular cryptocurrency wallets:\n\n" +
                   "Hardware Wallets:\n" +
                   "1. Ledger Nano X/S - Most secure, supports 1500+ coins\n" +
                   "2. Trezor Model T/One - High security, user-friendly\n\n" +
                   "Software Wallets:\n" +
                   "1. MetaMask - Best for Ethereum & ERC-20 tokens\n" +
                   "2. Trust Wallet - Mobile wallet, supports multiple chains\n" +
                   "3. Exodus - Desktop wallet, built-in exchange\n\n" +
                   "Exchange Wallets:\n" +
                   "1. Binance\n" +
                   "2. Coinbase\n" +
                   "Note: Exchange wallets are convenient but less secure than hardware wallets.";
        } else if (prompt.toLowerCase().contains("invest")) {
            return "Cryptocurrency Investment Tips:\n\n" +
                   "1. Do Your Research (DYOR)\n" +
                   "2. Never invest more than you can afford to lose\n" +
                   "3. Diversify your portfolio\n" +
                   "4. Use secure wallets\n" +
                   "5. Consider dollar-cost averaging\n" +
                   "6. Keep track of tax implications\n" +
                   "7. Be aware of market volatility\n" +
                   "8. Use reputable exchanges\n\n" +
                   "Would you like specific information about any of these points?";
        } else {
            return "I can help you with:\n\n" +
                   "1. Real-time cryptocurrency prices\n" +
                   "2. Market trends and analysis\n" +
                   "3. Top performers and market caps\n" +
                   "4. Trading volumes\n" +
                   "5. Wallet information\n" +
                   "6. Investment advice\n\n" +
                   "What specific information would you like to know?";
        }
    }
} 