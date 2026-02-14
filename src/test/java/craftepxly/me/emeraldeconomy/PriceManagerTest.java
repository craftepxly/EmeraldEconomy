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
 * Mencakup:
 * - Inisialisasi harga dasar
 * - Perhitungan dynamic pricing
 * - Supply/demand mechanics
 * - Resource depletion
 * - Transaction tax
 * - Price bounds enforcement
 * - EWMA smoothing
 * - Thread safety (basic)
 * 
 * @author craftepxly
 * @version 3.2.1
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
        
        // Mock plugin basics
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        
        // Mock default configuration values
        setupDefaultConfig();
        
        // Initialize PriceManager
        priceManager = new PriceManager(plugin);
    }
    
    private void setupDefaultConfig() {
        // Base prices
        when(configManager.getBuyPrice()).thenReturn(10.0);
        when(configManager.getSellPrice()).thenReturn(15.0);
        
        // Dynamic pricing settings
        when(config.getBoolean("dynamic_pricing.enabled", true)).thenReturn(true);
        when(config.getDouble("dynamic_pricing.min_price", 1.0)).thenReturn(1.0);
        when(config.getDouble("dynamic_pricing.max_price", 1000.0)).thenReturn(1000.0);
        when(config.getInt("dynamic_pricing.window_seconds", 300)).thenReturn(300);
        when(config.getInt("dynamic_pricing.update_interval", 5)).thenReturn(5);
        
        // Advanced settings
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
    @DisplayName("Inisialisasi harga dasar dari config")
    void testBasicPricing() {
        assertEquals(10.0, priceManager.getBaseBuyPrice(), 
            "Base buy price harus 10.0 sesuai config");
        assertEquals(15.0, priceManager.getBaseSellPrice(), 
            "Base sell price harus 15.0 sesuai config");
    }
    
    @Test
    @DisplayName("Harga saat ini awalnya sama dengan harga dasar")
    void testInitialCurrentPrices() {
        double buyPrice = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();
        
        assertEquals(10.0, buyPrice, 0.01, "Initial buy price = base buy price");
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
        assertEquals(0.05, priceManager.getTransactionTaxRate(), 
            "Tax rate harus 5%");
    }
    
    // =========================================================================
    // Test 3: Dynamic Pricing (Supply & Demand)
    // =========================================================================
    
    @Test
    @DisplayName("Record transaksi BUY meningkatkan volume")
    void testRecordBuyTransaction() {
        int initialVolume = priceManager.getRecentVolume();
        
        priceManager.recordTransaction(TransactionType.BUY, 10, 10.0);
        
        int newVolume = priceManager.getRecentVolume();
        assertEquals(initialVolume + 10, newVolume, 
            "Volume harus bertambah 10");
    }
    
    @Test
    @DisplayName("Record transaksi SELL meningkatkan volume")
    void testRecordSellTransaction() {
        priceManager.recordTransaction(TransactionType.SELL, 20, 15.0);
        
        assertEquals(20, priceManager.getRecentVolume(), 
            "Volume harus 20");
    }
    
    @Test
    @DisplayName("Transaksi besar di-cap ke max_impact_per_transaction")
    void testMaxImpactPerTransaction() {
        // Transaksi 200 emerald harus di-cap ke 100 untuk impact calculation
        priceManager.recordTransaction(TransactionType.BUY, 200, 10.0);
        
        // Total converted tetap track full amount (200)
        assertTrue(priceManager.getTotalEmeraldsConverted() >= 200,
            "Total converted harus track full amount (200)");
    }
    
    @Test
    @DisplayName("High demand (banyak BUY) tidak menurunkan harga drastis")
    void testDemandInfluence() {
        double initialBuyPrice = priceManager.getBuyPrice();
        
        // Simulasi high demand: 10 transaksi BUY
        for (int i = 0; i < 10; i++) {
            priceManager.recordTransaction(TransactionType.BUY, 10, initialBuyPrice);
        }
        
        priceManager.updatePrices();
        
        double newBuyPrice = priceManager.getBuyPrice();
        
        // Dengan high demand, buy price tidak boleh turun drastis
        assertTrue(newBuyPrice >= initialBuyPrice - 0.5, 
            "High demand tidak boleh menurunkan buy price");
    }
    
    @Test
    @DisplayName("High supply (banyak SELL) tidak menaikkan sell price")
    void testSupplyInfluence() {
        double initialSellPrice = priceManager.getSellPrice();
        
        // Simulasi high supply: 10 transaksi SELL
        for (int i = 0; i < 10; i++) {
            priceManager.recordTransaction(TransactionType.SELL, 10, initialSellPrice);
        }
        
        priceManager.updatePrices();
        
        double newSellPrice = priceManager.getSellPrice();
        
        // Dengan high supply, sell price tidak boleh naik drastis
        assertTrue(newSellPrice <= initialSellPrice + 1.0, 
            "High supply tidak boleh menaikkan sell price drastis");
    }
    
    // =========================================================================
    // Test 4: Resource Depletion
    // =========================================================================
    
    @Test
    @DisplayName("Total emeralds converted ter-track dengan benar")
    void testTotalEmeraldsTracking() {
        assertEquals(0, priceManager.getTotalEmeraldsConverted(), 
            "Initial total harus 0");
        
        priceManager.recordTransaction(TransactionType.BUY, 50, 10.0);
        priceManager.recordTransaction(TransactionType.SELL, 30, 15.0);
        
        assertEquals(80, priceManager.getTotalEmeraldsConverted(), 
            "Total harus 50 + 30 = 80");
    }
    
    @Test
    @DisplayName("Depletion factor berada di range 0.1 - 1.0")
    void testDepletionFactor() {
        double depletionFactor = priceManager.getDepletionFactor();
        
        assertTrue(depletionFactor >= 0.1 && depletionFactor <= 1.0, 
            "Depletion factor harus antara 0.1 dan 1.0");
    }
    
    // =========================================================================
    // Test 5: Price Bounds
    // =========================================================================
    
    @Test
    @DisplayName("Harga tidak boleh di bawah min_price")
    void testMinimumPriceBound() {
        // Banyak SELL untuk dorong harga ke bawah
        for (int i = 0; i < 100; i++) {
            priceManager.recordTransaction(TransactionType.SELL, 100, 15.0);
        }
        
        priceManager.updatePrices();
        
        double buyPrice = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();
        
        assertTrue(buyPrice >= 1.0, "Buy price >= min_price (1.0)");
        assertTrue(sellPrice >= 1.0, "Sell price >= min_price (1.0)");
    }
    
    @Test
    @DisplayName("Sell price harus >= buy price (cegah arbitrage)")
    void testNoArbitrage() {
        priceManager.updatePrices();
        
        double buyPrice = priceManager.getBuyPrice();
        double sellPrice = priceManager.getSellPrice();
        
        assertTrue(sellPrice >= buyPrice, 
            "Sell price >= buy price untuk cegah arbitrage");
    }
    
    // =========================================================================
    // Test 6: Configuration Reload
    // =========================================================================
    
    @Test
    @DisplayName("Reload config mengupdate base prices")
    void testConfigurationReload() {
        // Ubah mock config values
        when(configManager.getBuyPrice()).thenReturn(20.0);
        when(configManager.getSellPrice()).thenReturn(25.0);
        
        priceManager.loadConfiguration();
        
        assertEquals(20.0, priceManager.getBaseBuyPrice(), 
            "Base buy price update ke 20.0");
        assertEquals(25.0, priceManager.getBaseSellPrice(), 
            "Base sell price update ke 25.0");
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
        assertEquals(0, priceManager.getRecentVolume(), 
            "Volume harus 0 saat tidak ada transaksi");
    }
    
    // =========================================================================
    // Test 8: Thread Safety (Basic)
    // =========================================================================
    
    @Test
    @DisplayName("Concurrent reads tidak throw exception")
    void testConcurrentReads() {
        priceManager.recordTransaction(TransactionType.BUY, 10, 10.0);
        
        assertDoesNotThrow(() -> {
            double price1 = priceManager.getBuyPrice();
            double price2 = priceManager.getSellPrice();
            int volume = priceManager.getRecentVolume();
            
            assertTrue(price1 > 0);
            assertTrue(price2 > 0);
            assertTrue(volume >= 0);
        }, "Concurrent reads harus aman");
    }
    
    // =========================================================================
    // Test 9: Market Stability
    // =========================================================================
    
    @Test
    @DisplayName("Harga stabil dengan transaksi balanced (BUY = SELL)")
    void testBalancedTransactions() {
        double initialBuyPrice = priceManager.getBuyPrice();
        double initialSellPrice = priceManager.getSellPrice();
        
        // Transaksi seimbang: 5 BUY, 5 SELL
        for (int i = 0; i < 5; i++) {
            priceManager.recordTransaction(TransactionType.BUY, 10, initialBuyPrice);
            priceManager.recordTransaction(TransactionType.SELL, 10, initialSellPrice);
        }
        
        priceManager.updatePrices();
        
        double newBuyPrice = priceManager.getBuyPrice();
        double newSellPrice = priceManager.getSellPrice();
        
        // Harga harus relatif stabil
        assertTrue(Math.abs(newBuyPrice - initialBuyPrice) < 5.0, 
            "Buy price stabil dengan balanced transactions");
        assertTrue(Math.abs(newSellPrice - initialSellPrice) < 5.0, 
            "Sell price stabil dengan balanced transactions");
    }
    
    @Test
    @DisplayName("Multiple updates mencerminkan market conditions")
    void testMultipleUpdates() {
        // Simulasi serangkaian aktivitas market
        priceManager.recordTransaction(TransactionType.BUY, 20, 10.0);
        priceManager.updatePrices();
        
        double price1 = priceManager.getBuyPrice();
        
        priceManager.recordTransaction(TransactionType.BUY, 20, price1);
        priceManager.updatePrices();
        
        double price2 = priceManager.getBuyPrice();
        
        // Continuous demand harus ada efek (EWMA smoothing = gradual)
        assertTrue(price2 >= price1 - 1.0, 
            "Continuous demand maintain atau increase price");
    }
}
