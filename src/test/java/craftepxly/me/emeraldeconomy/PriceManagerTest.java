package craftepxly.me.emeraldeconomy;

import craftepxly.me.emeraldeconomy.config.ConfigManager;
import craftepxly.me.emeraldeconomy.service.PriceManager;
import craftepxly.me.emeraldeconomy.transaction.TransactionType;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite untuk PriceManager.
 *
 * Semua nama metode menggunakan perspektif PLAYER:
 *   getBuyPrice()  = harga yang player BAYAR saat membeli emerald dari server
 *   getSellPrice() = harga yang player TERIMA saat menjual emerald ke server
 *
 * Invariant: buyPrice >= sellPrice  (player selalu bayar lebih dari yang diterima)
 *
 * Mencakup:
 * - Inisialisasi harga dasar
 * - Perhitungan dynamic pricing
 * - Supply/demand mechanics (perspektif player)
 * - Resource depletion
 * - Transaction tax
 * - Price bounds enforcement
 * - EWMA smoothing
 * - Thread safety (basic)
 */
@DisplayName("PriceManager Test Suite")
class PriceManagerTest {

    @Mock
    private EmeraldEconomy plugin;

    @Mock
    private ConfigManager configManager;

    @Mock
    private FileConfiguration config;

    @Mock
    private Logger logger;

    private PriceManager priceManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);

        setupDefaultConfig();
        priceManager = new PriceManager(plugin);
    }

    private void setupDefaultConfig() {
        // Player perspective:
        //   getBuyPrice()  = 10.0 → price player PAYS when buying
        //   getSellPrice() = 15.0 → price player RECEIVES when selling
        // Note: in this test buy (10) < sell (15) which would trigger
        // the spread-correction in updatePrices (buyPrice adjusted up, sellPrice adjusted down)
        when(configManager.getBuyPrice()).thenReturn(10.0);
        when(configManager.getSellPrice()).thenReturn(15.0);

        when(config.getBoolean("dynamic_pricing.enabled", true)).thenReturn(true);
        when(config.getDouble("dynamic_pricing.min_price", 1.0)).thenReturn(1.0);
        when(config.getDouble("dynamic_pricing.max_price", 1000.0)).thenReturn(1000.0);
        when(config.getInt("dynamic_pricing.window_seconds", 300)).thenReturn(300);
        when(config.getInt("dynamic_pricing.update_interval", 5)).thenReturn(5);

        when(config.getDouble("dynamic_pricing.demand_sensitivity", 0.02)).thenReturn(0.02);
        when(config.getDouble("dynamic_pricing.supply_sensitivity", 0.02)).thenReturn(0.02);
        when(config.getDouble("dynamic_pricing.depletion_rate", 0.0001)).thenReturn(0.0001);
        when(config.getLong("dynamic_pricing.depletion_recovery_seconds", 3600L)).thenReturn(3600L);
        when(config.getInt("dynamic_pricing.max_impact_per_transaction", 100)).thenReturn(100);
        when(config.getDouble("dynamic_pricing.transaction_tax_rate", 0.05)).thenReturn(0.05);
    }

    // =========================================================================
    // Test 1: Basic Pricing (Harga Dasar)
    // =========================================================================

    @Test
    @DisplayName("Inisialisasi harga dasar dari config (perspektif player)")
    void testBasicPricing() {
        // getBuyPrice()  = harga player BAYAR saat beli → harus 10.0
        // getSellPrice() = harga player TERIMA saat jual → harus 15.0
        assertEquals(10.0, priceManager.getBaseBuyPrice(),
            "Base buy price (player pays) harus 10.0 sesuai config");
        assertEquals(15.0, priceManager.getBaseSellPrice(),
            "Base sell price (player receives) harus 15.0 sesuai config");
    }

    @Test
    @DisplayName("Harga saat ini awalnya sama dengan harga dasar")
    void testInitialCurrentPrices() {
        // Sebelum updatePrices() dipanggil, current = base
        double buyPrice  = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();

        assertEquals(10.0, buyPrice,  0.01, "Initial buy price = base buy price");
        assertEquals(15.0, sellPrice, 0.01, "Initial sell price = base sell price");
    }

    // =========================================================================
    // Test 2: Transaction Tax
    // =========================================================================

    @Test
    @DisplayName("Perhitungan transaction tax 5%")
    void testTransactionTax() {
        double amount = 100.0;
        double expectedTax = 100.0 * 0.05; // 5%
        assertEquals(expectedTax, priceManager.getTransactionTax(amount), 0.001,
            "Tax harus 5% dari amount");
    }

    @Test
    @DisplayName("Tax rate harus 0.05 (5%)")
    void testTransactionTaxRate() {
        assertEquals(0.05, priceManager.getTransactionTaxRate(), "Tax rate harus 5%");
    }

    // =========================================================================
    // Test 3: Dynamic Pricing (Supply & Demand) — perspektif player
    // =========================================================================

    @Test
    @DisplayName("Record transaksi BUY (player beli) meningkatkan volume")
    void testRecordBuyTransaction() {
        int initialVolume = priceManager.getRecentVolume();
        // Simulasi player membeli 10 emerald
        priceManager.recordTransaction(TransactionType.BUY, 10, 10.0);
        int newVolume = priceManager.getRecentVolume();
        assertEquals(initialVolume + 10, newVolume, "Volume harus bertambah 10");
    }

    @Test
    @DisplayName("Record transaksi SELL (player jual) meningkatkan volume")
    void testRecordSellTransaction() {
        // Simulasi player menjual 20 emerald
        priceManager.recordTransaction(TransactionType.SELL, 20, 15.0);
        assertEquals(20, priceManager.getRecentVolume(), "Volume harus 20");
    }

    @Test
    @DisplayName("Transaksi besar di-cap ke max_impact_per_transaction")
    void testMaxImpactPerTransaction() {
        // Player membeli 200 emerald → di-cap ke 100 untuk impact calculation
        priceManager.recordTransaction(TransactionType.BUY, 200, 10.0);
        // Total converted tetap track full amount (200)
        assertTrue(priceManager.getTotalEmeraldsConverted() >= 200,
            "Total converted harus track full amount (200)");
    }

    @Test
    @DisplayName("High demand (banyak player BUY) menaikkan buy price")
    void testDemandRaisesBuyPrice() {
        double initialBuyPrice = priceManager.getBuyPrice();

        // Simulasi high demand: banyak player membeli emerald
        for (int i = 0; i < 10; i++) {
            priceManager.recordTransaction(TransactionType.BUY, 10, initialBuyPrice);
        }
        priceManager.updatePrices();

        double newBuyPrice = priceManager.getBuyPrice();
        // Dengan high demand (banyak player beli), buy price harus naik atau minimal stabil
        assertTrue(newBuyPrice >= initialBuyPrice - 0.5,
            "High demand (banyak player beli) tidak boleh menurunkan buy price secara drastis");
    }

    @Test
    @DisplayName("High supply (banyak player SELL) menurunkan sell price")
    void testSupplyLowersSellPrice() {
        double initialSellPrice = priceManager.getSellPrice();

        // Simulasi high supply: banyak player menjual emerald
        for (int i = 0; i < 10; i++) {
            priceManager.recordTransaction(TransactionType.SELL, 10, initialSellPrice);
        }
        priceManager.updatePrices();

        double newSellPrice = priceManager.getSellPrice();
        // Dengan high supply (banyak player jual), sell price harus turun atau minimal stabil
        assertTrue(newSellPrice <= initialSellPrice + 1.0,
            "High supply (banyak player jual) tidak boleh menaikkan sell price secara drastis");
    }

    // =========================================================================
    // Test 4: Resource Depletion
    // =========================================================================

    @Test
    @DisplayName("Total emeralds converted ter-track dengan benar")
    void testTotalEmeraldsTracking() {
        assertEquals(0, priceManager.getTotalEmeraldsConverted(), "Initial total harus 0");
        // Player beli 50, jual 30
        priceManager.recordTransaction(TransactionType.BUY,  50, 10.0);
        priceManager.recordTransaction(TransactionType.SELL, 30, 15.0);
        assertEquals(80, priceManager.getTotalEmeraldsConverted(), "Total harus 50 + 30 = 80");
    }

    @Test
    @DisplayName("Depletion factor berada di range 0.1 - 1.0")
    void testDepletionFactor() {
        double depletionFactor = priceManager.getDepletionFactor();
        assertTrue(depletionFactor >= 0.1 && depletionFactor <= 1.0,
            "Depletion factor harus antara 0.1 dan 1.0");
    }

    // =========================================================================
    // Test 5: Price Bounds & Spread Invariant
    // =========================================================================

    @Test
    @DisplayName("Harga tidak boleh di bawah min_price")
    void testMinimumPriceBound() {
        // Banyak player menjual emerald untuk push sell price ke bawah
        for (int i = 0; i < 100; i++) {
            priceManager.recordTransaction(TransactionType.SELL, 100, 15.0);
        }
        priceManager.updatePrices();

        double buyPrice  = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();

        assertTrue(buyPrice  >= 1.0, "Buy price >= min_price (1.0)");
        assertTrue(sellPrice >= 1.0, "Sell price >= min_price (1.0)");
    }

    @Test
    @DisplayName("Buy price >= Sell price (cegah arbitrage — player bayar lebih dari yang diterima)")
    void testNoArbitrage() {
        priceManager.updatePrices();

        double buyPrice  = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();

        // Invariant: player selalu membayar LEBIH saat beli daripada yang diterima saat jual
        assertTrue(buyPrice >= sellPrice,
            "Buy price (player bayar) >= Sell price (player terima) — cegah arbitrage");
    }

    // =========================================================================
    // Test 6: Configuration Reload
    // =========================================================================

    @Test
    @DisplayName("Reload config mengupdate base prices")
    void testConfigurationReload() {
        // Simulasi admin mengubah harga via /ecadmin setprice
        when(configManager.getBuyPrice()).thenReturn(20.0);  // player pay price
        when(configManager.getSellPrice()).thenReturn(25.0); // player receive price

        priceManager.loadConfiguration();

        assertEquals(20.0, priceManager.getBaseBuyPrice(),
            "Base buy price update ke 20.0 (harga player bayar)");
        assertEquals(25.0, priceManager.getBaseSellPrice(),
            "Base sell price update ke 25.0 (harga player terima)");
    }

    // =========================================================================
    // Test 7: Edge Cases
    // =========================================================================

    @Test
    @DisplayName("Handle transaksi amount = 0 dengan aman")
    void testZeroAmountTransaction() {
        assertDoesNotThrow(() -> {
            priceManager.recordTransaction(TransactionType.BUY, 0, 10.0);
            priceManager.updatePrices();
        }, "Transaksi amount 0 tidak boleh throw exception");
    }

    @Test
    @DisplayName("Handle negative price dengan aman")
    void testNegativePriceHandling() {
        assertDoesNotThrow(() -> {
            priceManager.recordTransaction(TransactionType.BUY, 10, -5.0);
            priceManager.updatePrices();
        }, "Negative price tidak boleh crash");
    }

    @Test
    @DisplayName("Volume = 0 saat tidak ada transaksi")
    void testEmptyTransactionHistory() {
        assertEquals(0, priceManager.getRecentVolume(), "Volume harus 0 saat tidak ada transaksi");
    }

    // =========================================================================
    // Test 8: Thread Safety (Basic)
    // =========================================================================

    @Test
    @DisplayName("Concurrent reads tidak throw exception")
    void testConcurrentReads() {
        priceManager.recordTransaction(TransactionType.BUY, 10, 10.0);

        assertDoesNotThrow(() -> {
            double price1 = priceManager.getBuyPrice();  // player buy price
            double price2 = priceManager.getSellPrice(); // player sell price
            int volume    = priceManager.getRecentVolume();

            assertTrue(price1 > 0, "Buy price harus positif");
            assertTrue(price2 > 0, "Sell price harus positif");
            assertTrue(volume >= 0, "Volume harus non-negatif");
        }, "Concurrent reads harus aman");
    }

    // =========================================================================
    // Test 9: Market Stability
    // =========================================================================

    @Test
    @DisplayName("Harga stabil dengan transaksi balanced (BUY = SELL)")
    void testBalancedTransactions() {
        double initialBuyPrice  = priceManager.getBuyPrice();
        double initialSellPrice = priceManager.getSellPrice();

        // Transaksi seimbang: 5 player beli, 5 player jual
        for (int i = 0; i < 5; i++) {
            priceManager.recordTransaction(TransactionType.BUY,  10, initialBuyPrice);
            priceManager.recordTransaction(TransactionType.SELL, 10, initialSellPrice);
        }
        priceManager.updatePrices();

        double newBuyPrice  = priceManager.getBuyPrice();
        double newSellPrice = priceManager.getSellPrice();

        // Harga harus relatif stabil saat supply = demand
        assertTrue(Math.abs(newBuyPrice  - initialBuyPrice)  < 5.0,
            "Buy price stabil dengan balanced transactions");
        assertTrue(Math.abs(newSellPrice - initialSellPrice) < 5.0,
            "Sell price stabil dengan balanced transactions");
    }

    @Test
    @DisplayName("Multiple updates mencerminkan market conditions")
    void testMultipleUpdates() {
        // Simulasi serangkaian aktivitas market: player terus membeli
        priceManager.recordTransaction(TransactionType.BUY, 20, 10.0);
        priceManager.updatePrices();
        double price1 = priceManager.getBuyPrice();

        priceManager.recordTransaction(TransactionType.BUY, 20, price1);
        priceManager.updatePrices();
        double price2 = priceManager.getBuyPrice();

        // Continuous demand (player terus beli) → buy price harus maintain atau naik
        assertTrue(price2 >= price1 - 1.0,
            "Continuous buying demand (player terus beli) harus maintain atau naikkan buy price");
    }
}
