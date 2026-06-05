package com.faststore.kvstore.server;

import com.faststore.kvstore.engine.OffHeapEngine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MarketDataGenerator {

    private final OffHeapEngine engine;
    private final Random random = new Random();
    private final String[] tickers = {"AAPL", "TSLA", "NVDA", "AMZN", "MSFT", "GOOGL", "META", "BRK.B"};

    public MarketDataGenerator(OffHeapEngine engine) {
        this.engine = engine;
    }

    // Blast 5000 price updates every 100ms
    @Scheduled(fixedRate = 100)
    public void generateMarketData() {
        for (int i = 0; i < 5000; i++) {
            String ticker = tickers[random.nextInt(tickers.length)];
            double price = 100.0 + (random.nextDouble() * 900.0);
            int volume = random.nextInt(10000);
            long timestamp = System.currentTimeMillis();

            // JSON Payload
            String payload = String.format("{\"price\": %.2f, \"volume\": %d, \"ts\": %d}", price, volume, timestamp);
            
            // The key is prefixed with TICKER: to distinguish it from generic keys
            engine.put("TICKER:" + ticker, payload);
        }
    }
}
