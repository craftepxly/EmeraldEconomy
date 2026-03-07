package craftepxly.me.emeraldeconomy.storage;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.transaction.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/**
 * TransactionLogger — logs all transactions to a flat file (transactions.log).
 */
public class TransactionLogger {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // File object for transactions.log
    private File logFile;
    // Thread-safe queue for transactions waiting to be written
    private final BlockingQueue<Transaction> logQueue;
    // Background thread that writes transactions from queue to file
    private final Thread loggerThread;
    // Flag to control logger thread (volatile for thread visibility)
    private volatile boolean running = true;
    // PrintWriter for writing to file
    private PrintWriter writer;

    // [SECURITY FIX: MED-07] Maximum log file size before rotation (50 MB)
    private static final long MAX_LOG_SIZE = 50L * 1024L * 1024L;

    /**
     * Constructs a new TransactionLogger and starts background thread.
     *
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public TransactionLogger(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;
        // Create File object for transactions.log (path from config)
        String logFileName = plugin.getConfig().getString("transaction.log_file", "transactions.log");
        // [SECURITY FIX: LOW-02] Block path traversal characters in log file name
        if (logFileName.contains("..") || logFileName.contains("/") || logFileName.contains("\\")) {
            plugin.getLogger().warning("Invalid log file name: " + logFileName + ". Using default.");
            logFileName = "transactions.log";
        }
        this.logFile = new File(plugin.getDataFolder(), logFileName);
        // Create unbounded blocking queue (thread-safe)
        this.logQueue = new LinkedBlockingQueue<>();

        // Initialize log file
        try {
            // Check if file exists
            if (!logFile.exists()) {
                // Create parent directories
                logFile.getParentFile().mkdirs();
                // Create empty file
                logFile.createNewFile();
            }
            // Open PrintWriter in append mode
            this.writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
            // Write header if file is new
            writeHeader();
        } catch (IOException e) {
            // Log error if file initialization fails
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize transaction logger", e);
        }

        // Start logging thread (daemon thread)
        this.loggerThread = new Thread(this::processQueue, "EmeraldEconomy-Logger");
        this.loggerThread.setDaemon(true); // Thread dies when server stops
        this.loggerThread.start();
    }

    /**
     * Writes header to log file if file is empty.
     */
    private void writeHeader() {
        // Check if file is empty (length == 0)
        if (logFile.length() == 0) {
            // Write header comment
            writer.println("# EmeraldEconomy Transaction Log");
            writer.println("# Started: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println("# Format: Timestamp | UUID=<uuid> | name=<name> | TYPE=<type> | EMERALD=<amount> | MONEY=<amount> | PRICE=<price> | TXID=<id>");
            writer.println("#" + "=".repeat(120)); // Separator line
            // Flush to disk
            writer.flush();
        }
    }

    /**
     * Logs a transaction (queues it for async writing).
     *
     * @param transaction Transaction to log
     */
    public void log(Transaction transaction) {
        // Tolak log kalau logger sudah di-close
        // Reject log if logger is already closed
        if (!running || transaction == null) return;

        // Add transaction to queue (non-blocking)
        logQueue.offer(transaction);

        // Juga print ke console kalau dikonfigurasi
        // Also print to console if configured
        if (plugin.getConfig().getBoolean("transaction.enable_console_log", true)) {
            plugin.getLogger().info("Transaction: " + transaction);
        }
    }

    /**
     * Background thread that processes the log queue.
     * Runs continuously until logger is closed.
     */
    private void processQueue() {
        // Loop while logger is running
        while (running) {
            try {
                // Take transaction from queue (blocks until one is available)
                Transaction transaction = logQueue.take();
                // Write transaction to file
                writeTransaction(transaction);
            } catch (InterruptedException e) {
                // Check if we're shutting down
                if (!running) break;
                // Log warning if interrupted unexpectedly
                plugin.getLogger().warning("Transaction logger interrupted");
            } catch (Exception e) {
                // Log error if writing fails
                plugin.getLogger().log(Level.SEVERE, "Error writing transaction log", e);
            }
        }

        // Process remaining items before shutdown
        Transaction transaction;
        // Drain queue (poll returns null when empty)
        while ((transaction = logQueue.poll()) != null) {
            try {
                // Write remaining transaction
                writeTransaction(transaction);
            } catch (Exception e) {
                // Log error
                plugin.getLogger().log(Level.SEVERE, "Error writing final transaction log", e);
            }
        }
    }

    /**
     * Writes a transaction to the log file.
     *
     * @param transaction Transaction to write
     */
    private void writeTransaction(Transaction transaction) {
        // Check if writer exists
        if (writer != null) {
            // [SECURITY FIX: MED-07] Check file size before writing — rotate if exceeded
            if (logFile.length() > MAX_LOG_SIZE) {
                rotateLog();
            }
            // Write transaction string (calls transaction.toString())
            writer.println(transaction.toString());
            // Flush to disk immediately (ensures data is written)
            writer.flush();
        }
    }

    /**
     * Rotates the log file when it exceeds MAX_LOG_SIZE.
     * Renames current file to .old.log and creates a new empty log file.
     */
    private void rotateLog() {
        try {
            // Flush and close current writer
            writer.flush();
            writer.close();
            // Create rotated file name (transactions.old.log)
            File rotated = new File(logFile.getParent(),
                    logFile.getName().replace(".log", ".old.log"));
            // Delete old rotated file if exists
            if (rotated.exists()) {
                rotated.delete();
            }
            // Rename current log to .old.log
            logFile.renameTo(rotated);
            // Create new empty log file
            logFile = new File(logFile.getParent(), logFile.getName().replace(".old.log", ".log"));
            logFile.createNewFile();
            // Open new writer
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
            // Write header to new file
            writeHeader();
            // Log rotation event
            plugin.getLogger().info("Transaction log rotated (exceeded " + (MAX_LOG_SIZE / 1024 / 1024) + "MB)");
        } catch (IOException e) {
            // Log error if rotation fails
            plugin.getLogger().log(Level.SEVERE, "Failed to rotate transaction log", e);
        }
    }

    /**
     * Closes the transaction logger and flushes remaining queue.
     */
    public void close() {
        // Set running flag to false (signals thread to stop)
        running = false;

        // Check if logger thread exists
        if (loggerThread != null) {
            // Interrupt thread (wakes it up from blocking take())
            loggerThread.interrupt();
            try {
                // Wait up to 5 seconds for thread to finish
                loggerThread.join(5000);
            } catch (InterruptedException e) {
                // Log warning if thread doesn't terminate cleanly
                plugin.getLogger().warning("Transaction logger thread did not terminate cleanly");
            }
        }

        // Check if writer exists
        if (writer != null) {
            // Flush any buffered data
            writer.flush();
            // Close writer (closes file handle)
            writer.close();
        }
    }

    /**
     * Gets the log file.
     *
     * @return File object for transactions.log
     */
    public File getLogFile() {
        return logFile;
    }
}