package com.advatix.OMS.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaCompatibilityInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (!isMySql(metaData)) {
                return;
            }

            String catalog = connection.getCatalog();
            if (hasInventoryUnitStatusMismatch(metaData, catalog)) {
                jdbcTemplate.execute("ALTER TABLE inventory_units MODIFY COLUMN status VARCHAR(20) NOT NULL");
            }

            if (hasColumn(metaData, catalog, "order_items", "fulfilled_quantity")) {
                jdbcTemplate.execute("ALTER TABLE order_items DROP COLUMN fulfilled_quantity");
            }

            if (hasColumn(metaData, catalog, "order_items", "backordered_quantity")) {
                jdbcTemplate.execute("ALTER TABLE order_items DROP COLUMN backordered_quantity");
            }

            migrateLegacyShippedData(metaData, catalog);
            migrateTransactionHistoryStatusCode(metaData, catalog);
            migrateCarrierStatusMappingTable(metaData, catalog);
            jdbcTemplate.update("UPDATE orders SET status = 'READY_TO_SHIP' WHERE status = 'VEHICLE_ASSIGNED'");
            dropColumnIfExists(metaData, catalog, "orders", "assigned_vehicle");
            dropColumnIfExists(metaData, catalog, "orders", "shipping_label_generated_at");
            dropColumnIfExists(metaData, catalog, "orders", "shipping_label_number");
            dropColumnIfExists(metaData, catalog, "orders", "carrier");
            dropColumnIfExists(metaData, catalog, "orders", "service_level");
            dropColumnIfExists(metaData, catalog, "shipped_data", "assigned_vehicle");
            dropColumnIfExists(metaData, catalog, "carrier_sent_data", "processed");
            dropColumnIfExists(metaData, catalog, "carrier_sent_data", "received_at");
            dropColumnIfExists(metaData, catalog, "transaction_history", "performed_status_task");
            dropColumnIfExists(metaData, catalog, "transaction_history", "current_status");
            dropColumnIfExists(metaData, catalog, "transaction_history", "performed_by");

            dropUniqueIndexes(metaData, catalog, "products", "sku");
            dropUniqueIndexes(metaData, catalog, "products", "upc");
        }
    }

    private boolean isMySql(DatabaseMetaData metaData) throws SQLException {
        String databaseProductName = metaData.getDatabaseProductName();
        return databaseProductName != null && databaseProductName.toLowerCase().contains("mysql");
    }

    private boolean hasInventoryUnitStatusMismatch(DatabaseMetaData metaData, String catalog) throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, null, "inventory_units", "status")) {
            if (!columns.next()) {
                return false;
            }

            String typeName = columns.getString("TYPE_NAME");
            int columnSize = columns.getInt("COLUMN_SIZE");
            return !"VARCHAR".equalsIgnoreCase(typeName) || columnSize < 20;
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metaData.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private void migrateLegacyShippedData(DatabaseMetaData metaData, String catalog) throws SQLException {
        if (!hasColumn(metaData, catalog, "orders", "shipping_label_number")) {
            return;
        }

        jdbcTemplate.execute("""
                INSERT INTO shipped_data (
                    order_id,
                    shipping_label_number,
                    shipping_label_generated_at,
                    tracking_number,
                    carrier,
                    service_level
                )
                SELECT
                    o.id,
                    o.shipping_label_number,
                    COALESCE(o.shipping_label_generated_at, o.created_at),
                    COALESCE(o.tracking_number, CONCAT('OMS', REPLACE(o.order_number, 'ORD-', ''), 'IN')),
                    COALESCE(o.carrier, 'OMS EXPRESS'),
                    COALESCE(o.service_level, 'GROUND')
                FROM orders o
                WHERE o.shipping_label_number IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM shipped_data sd
                      WHERE sd.order_id = o.id
                  )
                """);
    }

    private void migrateTransactionHistoryStatusCode(DatabaseMetaData metaData, String catalog) throws SQLException {
        migrateTransactionHistoryPerformedAt(metaData, catalog);

        if (!hasColumn(metaData, catalog, "transaction_history", "status_code")) {
            jdbcTemplate.execute("ALTER TABLE transaction_history ADD COLUMN status_code INT NULL");
        }

        if (hasColumn(metaData, catalog, "transaction_history", "current_status")) {
            jdbcTemplate.execute("""
                    UPDATE transaction_history
                    SET status_code = CASE UPPER(TRIM(current_status))
                        WHEN 'CREATED' THEN 1
                        WHEN 'ALLOCATED' THEN 2
                        WHEN 'BACKORDERED' THEN 3
                        WHEN 'PICK_IN_PROGRESS' THEN 4
                        WHEN 'PICKED' THEN 5
                        WHEN 'READY_TO_PACK' THEN 6
                        WHEN 'PACKED' THEN 7
                        WHEN 'READY_TO_SHIP' THEN 8
                        WHEN 'SHIPPED' THEN 9
                        WHEN 'IN_TRANSIT' THEN 10
                        WHEN 'OUT_FOR_DELIVERY' THEN 11
                        WHEN 'DELIVERED' THEN 12
                        WHEN 'CANCELLED' THEN 13
                        WHEN 'REJECTED' THEN 14
                        ELSE status_code
                    END
                    WHERE current_status IS NOT NULL
                    """);
        }

        jdbcTemplate.execute("UPDATE transaction_history SET status_code = 1 WHERE status_code IS NULL");
        jdbcTemplate.execute("ALTER TABLE transaction_history MODIFY COLUMN status_code INT NOT NULL");
    }

    private void migrateTransactionHistoryPerformedAt(DatabaseMetaData metaData, String catalog) throws SQLException {
        if (!hasColumn(metaData, catalog, "transaction_history", "performed_at")) {
            jdbcTemplate.execute("ALTER TABLE transaction_history ADD COLUMN performed_at DATETIME NULL");
        }

        if (hasColumn(metaData, catalog, "transaction_history", "created_at")) {
            jdbcTemplate.execute("""
                    UPDATE transaction_history
                    SET performed_at = COALESCE(performed_at, created_at)
                    WHERE created_at IS NOT NULL
                    """);
            dropColumnIfExists(metaData, catalog, "transaction_history", "created_at");
        }

        jdbcTemplate.execute("UPDATE transaction_history SET performed_at = NOW() WHERE performed_at IS NULL");
        jdbcTemplate.execute("ALTER TABLE transaction_history MODIFY COLUMN performed_at DATETIME NOT NULL");
    }

    private void migrateCarrierStatusMappingTable(DatabaseMetaData metaData, String catalog) throws SQLException {
        if (!hasColumn(metaData, catalog, "carrier_status_mapping", "status_code")) {
            jdbcTemplate.execute("ALTER TABLE carrier_status_mapping ADD COLUMN status_code INT NULL");
        }

        if (!hasColumn(metaData, catalog, "carrier_status_mapping", "carrier_status")) {
            jdbcTemplate.execute("ALTER TABLE carrier_status_mapping ADD COLUMN carrier_status VARCHAR(60) NULL");
        }

        if (hasColumn(metaData, catalog, "carrier_status_mapping", "carrier_status")) {
            jdbcTemplate.execute("""
                    UPDATE carrier_status_mapping
                    SET status_code = CASE UPPER(TRIM(carrier_status))
                        WHEN 'CREATED' THEN 1
                        WHEN 'ALLOCATED' THEN 2
                        WHEN 'BACKORDERED' THEN 3
                        WHEN 'PICK_IN_PROGRESS' THEN 4
                        WHEN 'PICKED' THEN 5
                        WHEN 'READY_TO_PACK' THEN 6
                        WHEN 'PACKED' THEN 7
                        WHEN 'READY_TO_SHIP' THEN 8
                        WHEN 'SHIPPED' THEN 9
                        WHEN 'IN_TRANSIT' THEN 10
                        WHEN 'OUT_FOR_DELIVERY' THEN 11
                        WHEN 'DELIVERED' THEN 12
                        WHEN 'CANCELLED' THEN 13
                        WHEN 'REJECTED' THEN 14
                        ELSE status_code
                    END
                    WHERE carrier_status IS NOT NULL
                    """);
        }

        jdbcTemplate.execute("""
                UPDATE carrier_status_mapping
                SET carrier_status = CASE status_code
                    WHEN 1 THEN 'CREATED'
                    WHEN 2 THEN 'ALLOCATED'
                    WHEN 3 THEN 'BACKORDERED'
                    WHEN 4 THEN 'PICK_IN_PROGRESS'
                    WHEN 5 THEN 'PICKED'
                    WHEN 6 THEN 'READY_TO_PACK'
                    WHEN 7 THEN 'PACKED'
                    WHEN 8 THEN 'READY_TO_SHIP'
                    WHEN 9 THEN 'SHIPPED'
                    WHEN 10 THEN 'IN_TRANSIT'
                    WHEN 11 THEN 'OUT_FOR_DELIVERY'
                    WHEN 12 THEN 'DELIVERED'
                    WHEN 13 THEN 'CANCELLED'
                    WHEN 14 THEN 'REJECTED'
                    ELSE carrier_status
                END
                WHERE status_code IS NOT NULL
                """);

        insertCarrierStatusMappingIfMissing(1, "CREATED");
        insertCarrierStatusMappingIfMissing(2, "ALLOCATED");
        insertCarrierStatusMappingIfMissing(3, "BACKORDERED");
        insertCarrierStatusMappingIfMissing(4, "PICK_IN_PROGRESS");
        insertCarrierStatusMappingIfMissing(5, "PICKED");
        insertCarrierStatusMappingIfMissing(6, "READY_TO_PACK");
        insertCarrierStatusMappingIfMissing(7, "PACKED");
        insertCarrierStatusMappingIfMissing(8, "READY_TO_SHIP");
        insertCarrierStatusMappingIfMissing(9, "SHIPPED");
        insertCarrierStatusMappingIfMissing(10, "IN_TRANSIT");
        insertCarrierStatusMappingIfMissing(11, "OUT_FOR_DELIVERY");
        insertCarrierStatusMappingIfMissing(12, "DELIVERED");
        insertCarrierStatusMappingIfMissing(13, "CANCELLED");
        insertCarrierStatusMappingIfMissing(14, "REJECTED");

        jdbcTemplate.execute("UPDATE carrier_status_mapping SET carrier_status = 'UNKNOWN' WHERE carrier_status IS NULL");
        jdbcTemplate.execute("ALTER TABLE carrier_status_mapping MODIFY COLUMN status_code INT NOT NULL");
        jdbcTemplate.execute("ALTER TABLE carrier_status_mapping MODIFY COLUMN carrier_status VARCHAR(60) NOT NULL");
    }

    private void insertCarrierStatusMappingIfMissing(int statusCode, String carrierStatus) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM carrier_status_mapping WHERE status_code = ?",
                Integer.class,
                statusCode);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO carrier_status_mapping (status_code, carrier_status) VALUES (?, ?)",
                    statusCode,
                    carrierStatus);
        }
    }

    private void dropColumnIfExists(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        if (hasColumn(metaData, catalog, tableName, columnName)) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`");
        }
    }

    private void dropUniqueIndexes(DatabaseMetaData metaData, String catalog, String tableName, String columnName) throws SQLException {
        try (ResultSet indexes = metaData.getIndexInfo(catalog, null, tableName, true, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                String indexedColumn = indexes.getString("COLUMN_NAME");
                if (indexName == null || "PRIMARY".equalsIgnoreCase(indexName)) {
                    continue;
                }
                if (columnName.equalsIgnoreCase(indexedColumn)) {
                    jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
                }
            }
        }
    }
}
