

## Default admin

- Username: `admin`
- Password: `admin123`

## Database

The application now uses MySQL for normal runtime.

- URL: `jdbc:mysql://localhost:3306/oms_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata`
- Username: `root`
- Password: `jiit`

Schema compatibility note:

- On startup, the application checks the MySQL definition of `inventory_units.status`.
- If an older schema is still incompatible, the app automatically changes the column to `VARCHAR(20) NOT NULL`.
- This prevents picker flow errors such as `Data truncated for column 'status'` when inventory is moved to `CONTAINER`.
- On startup, legacy order rows with status `VEHICLE_ASSIGNED` are moved back to `READY_TO_SHIP`.
- Legacy vehicle columns are dropped from `orders` and `shipped_data`.
- On startup, the application also removes older unique indexes from `products.sku` and `products.upc`.
- This allows admins to create another product row with the same product name, SKU, and UPC.

Backorder scheduler note:

- The application runs a backorder allocation scheduler every 5 minutes.
- Property: `oms.backorder.scheduler-delay-ms=300000`
- If stock is increased later and matching `BIN` serial inventory exists, pending backorders are allocated automatically.

Carrier status scheduler note:

- The application runs `UpdateOrderTableScheduler` every 5 minutes.
- Carrier webhook/API requests are saved first in `carrier_sent_data`.
- In `carrier_sent_data`, `webhook = 0` means the carrier update is pending, and `webhook = 1` means the update has been applied to the order.
- The scheduler reads carrier rows where `webhook = 0`, finds the matching status in `carrier_status_mapping`, updates the related order, and then changes `webhook` to `1`.
- Carrier status mapping is always read from the database table. The scheduler does not hardcode carrier status decisions.
- Unknown carrier statuses are logged and skipped without changing the order.

### Login

`POST /api/auth/login`

```json
{
  "username": "admin",
  "password": "admin123"
}
```

### Logout

`POST /api/auth/logout`

### Create client

`POST /api/admin/clients`

```json
{
  "name": "Acme Retail",
  "clientCode": "ACME",
  "contactEmail": "ops@acme.com",
  "contactPhone": "9999999999"
}
```

### Create product

`POST /api/admin/products`

```json
{
  "productName": "Widget A",
  "clientName": "Acme Retail",
  "sku": "SKU-100",
  "productDescription": "Fast moving item",
  "upc": "UPC-100",
  "weight": 1.250,
  "unitPrice": 199.99,
  "openingStock": 50,
  "warehouseLocation": "WH-A1"
}
```

Product creation behavior:

- A product row is created in `products`.
- A matching stock row is created in `inventory`.
- Serialized inventory units are created in `inventory_units` with status `BIN`.
- Product name, SKU, and UPC can be repeated for another product row.
- When a SKU is repeated, generated serial numbers continue from the existing SKU sequence to avoid duplicate serial numbers.
- Example: if `SKU-100-SN-1` through `SKU-100-SN-50` already exist, the next product created with `SKU-100` starts at `SKU-100-SN-51`.

### View clients

`GET /api/admin/clients`

### View products

`GET /api/admin/products`

### View inventory

`GET /api/admin/inventory`

### Create order

`POST /api/orders`

```json
{
  "customerName": "Ravi Kumar",
  "customerEmail": "ravi@example.com",
  "shippingAddress": {
    "addressLine1": "12 MG Road",
    "city": "Bengaluru",
    "state": "Karnataka",
    "postalCode": "560001",
    "country": "India"
  },
  "items": [
    {
      "sku": "SKU-100",
      "quantity": 2
    }
  ]
}
```

Order creation behavior:

- If full stock is available, the order is returned as `ALLOCATED`.
- If only partial stock is available, the available quantity is allocated immediately and the remaining quantity is moved to backorder.
- In that partial case, the order is returned as `BACKORDERED`.
- Each order item response now includes:
  - `quantity`: requested quantity
  - `fulfilledQuantity`: currently allocated quantity
  - `backorderedQuantity`: remaining pending quantity from the separate `back_orders` table

### Pick allocated order

`POST /api/orders/{orderNumber}/pick`

Request JSON:

```json
{
  "pickerId": "PICKER-01",
  "containerId": "TOTE-100",
  "serialNumbers": [
    "SKU-100-SN-1",
    "SKU-100-SN-2"
  ]
}
```



Validations:

- order must be in `ALLOCATED`
- `pickerId`, `containerId`, and `serialNumbers` are required
- serial numbers must be unique
- serial count must exactly match allocated order quantity
- serials must belong to the allocated inventory for that order
- quoted or comma-suffixed serial values copied from JSON are normalized before validation

Processing:

- assign picker to the order
- save the destination container/tote
- normalize and validate each serial number
- move inventory units from `ALLOCATED` to `CONTAINER`
- reduce reserved quantity for the related inventory
- update order status from `ALLOCATED` to `PICK_IN_PROGRESS` and then `READY_TO_PACK` in the same request

Notes:

- This endpoint performs the complete picker confirmation flow in one API call.
- Partial picking is not supported in the current implementation.
- If an order has `9` allocated units, the request must contain `9` valid serial numbers, even if the original requested quantity was higher and some units are still backordered.
- The final response status is `READY_TO_PACK`.

Accepted serial input examples:

```text
SKU-100-SN-1
SKU-100-SN-2
```

```text
"SKU-100-SN-1",
"SKU-100-SN-2"
```

### Pack ready order

`POST /api/orders/{orderNumber}/pack`

Request JSON:

```json
{
  "weight": 2.500
}
```

Validations:

- order must be in `READY_TO_PACK`
- `weight` is required
- `weight` must be at least `0.001`

Box assignment:

- `SMALL_BOX`: weight up to `1.000`
- `MEDIUM_BOX`: weight greater than `1.000` and up to `5.000`
- `LARGE_BOX`: weight greater than `5.000` and up to `10.000`
- `EXTRA_LARGE_BOX`: weight greater than `10.000`

Processing:

- shipping enters the packed product weight
- the system assigns a box based on the weight
- package weight and assigned box are saved on the order
- order status changes from `READY_TO_PACK` to `PACKED`

Response example:

```json
{
  "orderNumber": "ORD-20260430231503497",
  "weight": 2.500,
  "assignedBox": "MEDIUM_BOX",
  "status": "PACKED",
  "message": "Box assigned and order packed"
}
```

### Generate shipping label

`POST /api/orders/{orderNumber}/shipping-label`

Validations:

- order must be in `PACKED`

Processing:

- the system generates a shipping label number and tracking number
- carrier, service level, ship-from, destination, package details, barcode text, and a PNG label image are returned
- label number, tracking number, carrier, service level, and generated timestamp are saved in `shipped_data`
- order status changes from `PACKED` to `READY_TO_SHIP`
- shipment id, tracking number, shipment status, order status code, and updated timestamp are saved on `orders`

Response example:

```json
{
  "orderNumber": "ORD-20260430231503497",
  "shippingLabelNumber": "SL-20260430231503497",
  "trackingNumber": "OMS20260430231503497IN",
  "carrier": "OMS EXPRESS",
  "serviceLevel": "GROUND",
  "generatedAt": "2026-05-04T22:31:34",
  "shipFromAddress": "OMS Fulfillment Center, WH-A1, Bengaluru, Karnataka 560001, India",
  "destinationAddress": "25 Indiranagar, Bengaluru, Karnataka, 560038, India",
  "packageWeight": 2.500,
  "assignedBox": "MEDIUM_BOX",
  "barcodeValue": "OMS EXPRESS|GROUND|OMS20260430231503497IN|ORD-20260430231503497",
  "labelImageContentType": "image/png",
  "labelImageBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "labelImageDataUrl": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "status": "READY_TO_SHIP",
  "message": "Shipping label generated"
}
```

The `labelImageDataUrl` value can be assigned directly to an HTML `img` tag `src` or downloaded as a `.png` file.

### Create LPN

`POST /api/lpns`

```json
{
  "createdBy": "PACKER-01",
  "remarks": "Pallet for Bengaluru dispatch"
}
```

Processing:

- generates a unique sequence-based LPN number like `LPN-20260518-0001`
- creates an LPN shipment grouping unit
- starts with status `CREATED`

Response example:

```json
{
  "lpnNumber": "LPN-20260518-0001",
  "createdAt": "2026-05-18T15:30:00",
  "createdBy": "PACKER-01",
  "carrierName": null,
  "shipmentStatus": "CREATED",
  "totalOrders": 0,
  "totalQuantity": 0,
  "totalWeight": 0,
  "remarks": "Pallet for Bengaluru dispatch",
  "truckloadNumber": null,
  "orderNumbers": [],
  "message": "LPN created"
}
```

### Attach orders to LPN

`POST /api/lpns/{lpnNumber}/orders`

```json
{
  "orderNumbers": [
    "ORD-20260518153000001",
    "ORD-20260518153000002"
  ]
}
```

Validations:

- shipped orders cannot be added
- cancelled or rejected orders cannot be added
- orders already attached directly to a truckload cannot be added
- an order can belong to only one active LPN
- shipped LPNs cannot be modified

Processing:

- links each order to the LPN
- recalculates `totalOrders`, `totalQuantity`, and `totalWeight`

### Assign carrier to LPN

`POST /api/lpns/{lpnNumber}/carrier`

```json
{
  "carrierName": "FedEx"
}
```

Valid carriers:

- `Delhivery`
- `Blue Dart`
- `FedEx`

Processing:

- saves the carrier on the LPN
- moves LPN shipment status to `CARRIER_ASSIGNED`
- carrier-assigned LPNs can be attached to a truckload

### Create truckload

`POST /api/truckloads`

```json
{
  "remarks": "Outbound batch"
}
```

Processing:

- creates the final outbound shipment batch
- generates a truckload number like `TL-20260518153000001`
- starts with shipment status `CREATED`

### Attach LPNs to truckload

`POST /api/truckloads/{truckloadNumber}/lpns`

```json
{
  "lpnNumbers": [
    "LPN-20260518-0001"
  ]
}
```

Validations:

- only carrier-assigned LPNs can be attached
- shipped LPNs cannot be attached or modified
- an LPN can belong to only one truckload

### Attach direct orders to truckload

`POST /api/truckloads/{truckloadNumber}/orders`

```json
{
  "orderNumbers": [
    "ORD-20260518153000001"
  ]
}
```

Validations:

- orders inside an LPN cannot be attached directly to truckload
- shipped orders cannot be added
- cancelled or rejected orders cannot be added
- one order can belong to either one active LPN or one direct truckload, not both

### Assign carrier to truckload

`POST /api/truckloads/{truckloadNumber}/carrier`

```json
{
  "carrierName": "FedEx"
}
```

Valid carriers:

- `Delhivery`
- `Blue Dart`
- `FedEx`

### Dispatch truckload

`POST /api/truckloads/{truckloadNumber}/dispatch`

Validations:

- carrier must be assigned before dispatch
- truckload must contain at least one LPN or one direct order
- shipped truckloads cannot be modified

Processing:

- marks truckload as `SHIPPED`
- marks all attached LPNs as `SHIPPED`
- marks all orders inside attached LPNs as `SHIPPED`
- marks all direct truckload orders as `SHIPPED`
- runs inside a transaction so failures roll back together

### View shipping order table

`GET /api/orders/shipping-table`

Returns packed and dispatch-stage orders with order info, assigned box, label number, tracking number, carrier, service level, and destination address.

### Carrier shipment update API

`POST /api/carrier`

Carrier APIs can send shipment status updates after the order is shipped. The request is stored in `carrier_sent_data`; the order is updated later by the scheduler.

Request JSON:

```json
{
  "shipment_id": "SHIP-20260525161246745",
  "carrier_status": "OUT_FOR_DELIVERY"
}
```

Response example:

```json
{
  "shipment_id": "SHIP-20260525161246745",
  "carrier_status": "OUT_FOR_DELIVERY",
  "message": "Carrier data inserted"
}
```

Important behavior:

- `shipment_id` is matched against `orders.shipment_id`.
- `status_time` is generated by the backend when the carrier request is saved.
- The scheduler also tries `orders.tracking_number` and `orders.order_number` as fallback identifiers.
- `carrier_status` must exist in `carrier_status_mapping`.
- If a mapping is found, the scheduler writes:
  - `orders.shipment_status`
  - `orders.order_status_code`
  - `orders.status`, when the mapped status exists in the `OrderStatus` enum
  - `orders.updated_at`
- After a successful update, `carrier_sent_data.webhook` changes from `0` to `1`.
- Duplicate scheduler updates are safe because rows with `webhook = 1` are not picked again.

### Manual carrier update and order sync API

`POST /api/carrier/update-order`

Use this endpoint when you want to manually send a carrier update and update the matching order immediately, without waiting for the 5-minute scheduler.

Request JSON:

```json
  {
    "shipment_id": "SHIP-20260525161246745",
    "carrier_status": "OUT_FOR_DELIVERY"
  }
```

Response example:

```json
{
  "carrier_data_id": 10,
  "shipment_id": "SHIP-20260525161246745",
  "carrier_status": "OUT_FOR_DELIVERY",
  "webhook": 1,
  "message": "Order status updated"
}
```

How it works:

- Inserts the request into `carrier_sent_data`.
- Reads `carrier_status_mapping` for the status code.
- Finds the order using `shipment_id`, with fallback checks on tracking number and order number.
- Updates order shipment status and order status code immediately.
- Changes the new carrier update row `webhook` value from `0` to `1` after a successful order update.
- If the status is not mapped or the order is not found, the row stays at `webhook = 0` and the response explains why.

## Schema overview

- `admin_users`: session-authenticated admin credentials
- `clients`: client master data
- `products`: product master data linked to clients
- `inventory`: stock by product
- `inventory_units`: serialized unit inventory used for allocation and picking
- `orders`: compact order header with customer, address, picker, and packing data
- `order_items`: ordered items linked to the order
- `back_orders`: pending unfulfilled quantities for partially allocated orders
- `shipped_data`: shipping label, tracking, carrier, and service data linked to one order
- `carrier_status_mapping`: carrier status master data with internal numeric status code
- `carrier_sent_data`: raw carrier updates; `webhook = 0` is pending and `webhook = 1` is applied
- `lpns`: pallet/container shipment grouping records
- `lpn_sequences`: daily LPN sequence counters used for unique LPN number generation
- `truckloads`: final outbound shipment batches

## Database schema

The application uses MySQL as the primary runtime database. Spring Data JPA and Hibernate generate the tables from the entity model. The schema is designed around admin-managed master data and customer-facing order transactions.

### 1. `admin_users`

Purpose: stores admin login credentials for normal session-based authentication.

Main columns:

- `id` bigint primary key
- `username` varchar(100) unique not null
- `password` varchar(255) not null
- `full_name` varchar(150) not null

Notes:

- This table is used only for admin login.
- Passwords are stored in encrypted form using BCrypt.
- One row represents one admin account.

### 2. `clients`

Purpose: stores client master records created by the admin.

Main columns:

- `id` bigint primary key
- `name` varchar(150) unique not null
- `client_code` varchar(50) unique not null
- `contact_email` varchar(120) not null
- `contact_phone` varchar(30) not null
- `active` boolean not null

Notes:

- Each client is a business/customer account managed by admin.
- `client_code` can be treated as a business identifier.
- A client can own many products.

### 3. `products`

Purpose: stores product master data.

Main columns:

- `id` bigint primary key
- `product_name` varchar(150) not null
- `client_id` bigint not null
- `sku` varchar(80) not null
- `product_description` varchar(1000) not null
- `upc` varchar(80) not null
- `weight` numeric(10,3) not null
- `unit_price` numeric(12,2) not null

Foreign keys:

- `client_id` references `clients(id)`

Notes:

- Every product belongs to exactly one client.
- Product name, SKU, and UPC can be repeated across product rows.
- Product pricing and physical information are stored here.
- If more than one product row has the same SKU, order creation chooses the matching product row with the best available allocatable stock.

### 4. `inventory`

Purpose: stores stock information for each product.

Main columns:

- `id` bigint primary key
- `product_id` bigint unique not null
- `available_quantity` int not null
- `reserved_quantity` int not null
- `warehouse_location` varchar(80) not null

Foreign keys:

- `product_id` references `products(id)`

Notes:

- This table has a one-to-one relationship with `products`.
- `available_quantity` is the stock currently available for ordering.
- `reserved_quantity` is the stock already committed by created orders.
- When an order is created, available stock decreases and reserved stock increases only for the quantity that is actually allocated.
- Matching serialized units are allocated from `BIN` inventory.
- Later restocks can be consumed by the backorder scheduler.

### 5. `orders`

Purpose: stores order header information for each customer order.

Main columns:

- `id` bigint primary key
- `order_number` varchar(40) unique not null
- `customer_name` varchar(120) not null
- `customer_email` varchar(120) not null
- `status` varchar(20) not null
- `assigned_picker` varchar(80) null
- `pick_container` varchar(80) null
- `package_weight` numeric(10,3) null
- `assigned_box` varchar(80) null
- `shipment_id` varchar(80) null
- `tracking_number` varchar(80) null
- `shipment_status` varchar(60) null
- `order_status_code` int null
- `updated_at` timestamp null
- `lpn_id` bigint null
- `direct_truckload_id` bigint null
- `serviceable` boolean not null
- `created_at` timestamp not null
- `address_line1` varchar(200) not null
- `address_line2` varchar(200) null
- `city` varchar(100) not null
- `state` varchar(100) not null
- `postal_code` varchar(20) not null
- `country` varchar(100) not null

Notes:

- This is the order header table.
- Shipping address is embedded directly into this table.
- One order can contain many order item rows.
- `order_number` is generated by the application and is unique.
- After picking, packing details are stored as `package_weight` and `assigned_box`.
- After label generation, carrier label details are stored in `shipped_data`; shipment id, tracking number, shipment status, and status code are stored on `orders`.
- For shipment dispatch, one order can belong to either one active LPN or one truckload directly.

### 6. `shipped_data`

Purpose: stores shipping label and tracking data without making the main `orders` table too wide.

Main columns:

- `id` bigint primary key
- `order_id` bigint unique not null
- `shipping_label_number` varchar(80) not null
- `shipping_label_generated_at` timestamp not null
- `tracking_number` varchar(80) not null
- `carrier` varchar(80) not null
- `service_level` varchar(80) not null

Foreign keys:

- `order_id` references `orders(id)`

Notes:

- One order has zero or one shipped data row.
- The row is created when shipping generates the PNG label.
- Dispatch grouping is handled by LPN and truckload tables.

### 7. `lpns`

Purpose: groups multiple orders into one pallet/container for shipment.

Main columns:

- `id` bigint primary key
- `lpn_number` varchar(40) unique not null
- `created_at` timestamp not null
- `created_by` varchar(80) not null
- `carrier_name` varchar(80) null
- `shipment_status` varchar(30) not null
- `total_orders` int not null
- `total_quantity` int not null
- `total_weight` numeric(12,3) not null
- `remarks` varchar(500) null
- `truckload_id` bigint null

Notes:

- One LPN can contain many orders.
- One order can belong to only one active LPN.
- LPNs cannot be modified after dispatch.
- Carrier must be assigned before attaching the LPN to a truckload.

### 8. `lpn_sequences`

Purpose: stores daily counters for thread-safe LPN number generation.

Main columns:

- `sequence_date` date primary key
- `next_value` int not null

Notes:

- LPN numbers use the format `LPN-yyyyMMdd-0001`.
- The sequence is locked while generating the next number.

### 9. `truckloads`

Purpose: represents the final outbound shipment batch.

Main columns:

- `id` bigint primary key
- `truckload_number` varchar(50) unique not null
- `carrier_name` varchar(80) null
- `dispatch_date` timestamp null
- `shipment_status` varchar(30) not null
- `remarks` varchar(500) null

Notes:

- One truckload can contain multiple LPNs.
- One truckload can also contain multiple direct orders that are not inside an LPN.
- Dispatching a truckload marks the truckload, attached LPNs, and all attached orders as `SHIPPED`.

### 10. `inventory_units`

Purpose: stores serialized inventory units used for order allocation and picking.

Main columns:

- `id` bigint primary key
- `inventory_id` bigint not null
- `allocated_order_id` bigint null
- `serial_number` varchar(120) unique not null
- `status` varchar(20) not null
- `current_location` varchar(80) not null
- `container_location` varchar(80) null

Notes:

- Each row represents one serialized physical unit.
- Valid application values are `BIN`, `ALLOCATED`, and `CONTAINER`.
- Units start in `BIN`.
- Allocation moves units to `ALLOCATED` against an order.
- Picking moves units to `CONTAINER` and updates location to the container/tote.
- On MySQL startup, the application repairs older incompatible `status` column definitions if needed.

### 11. `order_items`

Purpose: stores line items for each order.

Main columns:

- `id` bigint primary key
- `order_id` bigint not null
- `product_id` bigint not null
- `quantity` int not null
- `unit_price` numeric(12,2) not null
- `line_total` numeric(12,2) not null

Foreign keys:

- `order_id` references `orders(id)`
- `product_id` references `products(id)`

Notes:

- Each row represents one ordered product inside one order.
- `unit_price` is copied from the product at the time of order creation.
- `line_total` = `unit_price * quantity`.

### 12. `back_orders`

Purpose: stores pending unfulfilled quantities separately from the main order lines.

Main columns:

- `id` bigint primary key
- `order_id` bigint not null
- `product_id` bigint not null
- `pending_quantity` int not null
- `created_at` timestamp not null

Foreign keys:

- `order_id` references `orders(id)`
- `product_id` references `products(id)`

Notes:

- A row exists only when an order line cannot be fully allocated immediately.
- `pending_quantity` is the quantity still waiting for stock.
- When the scheduler fulfills the remainder, the related `back_orders` row is removed.

### 13. `carrier_status_mapping`

Purpose: stores carrier status master data and the internal numeric status code used by order synchronization.

Main columns:

- `id` bigint primary key
- `carrier_status` varchar(60) unique not null
- `status_code` int not null

Default seeded values:

```text
CREATED = 1
ALLOCATED = 2
BACKORDERED = 3
PICK_IN_PROGRESS = 4
PICKED = 5
READY_TO_PACK = 6
PACKED = 7
READY_TO_SHIP = 8
SHIPPED = 9
IN_TRANSIT = 10
OUT_FOR_DELIVERY = 11
DELIVERED = 12
CANCELLED = 13
REJECTED = 14
```

Notes:

- This table is seeded on startup if rows are missing.
- The scheduler reads this table for every carrier status update.
- Status behavior can be changed by updating this table instead of changing scheduler code.

### 14. `carrier_sent_data`

Purpose: stores every incoming carrier update before it is applied to the order table.

Main columns:

- `id` bigint primary key
- `shipment_id` varchar(80) not null
- `carrier_status` varchar(60) not null
- `webhook` int not null default 0
- `status_time` timestamp null

Notes:

- API requests insert rows into this table.
- The scheduler reads only rows where `webhook = 0`.
- Unknown statuses or missing orders are logged and left at `webhook = 0` for later correction.
- Successfully applied rows are changed to `webhook = 1` so duplicate scheduler runs do not repeat the same update.

## Table relationships

The tables are connected in a straightforward transactional flow.

### `clients` -> `products`

Relationship:

- One-to-many

Meaning:

- One client can have many products.
- One product belongs to only one client.

Implemented by:

- `products.client_id` foreign key to `clients.id`

Example:

- Client `Acme Retail` can have products `Widget A`, `Widget B`, and `Widget C`.

### `products` -> `inventory`

Relationship:

- One-to-one

Meaning:

- Each product has one inventory record.
- Each inventory record belongs to one product only.

Implemented by:

- `inventory.product_id` unique foreign key to `products.id`

Example:

- Product `SKU-100` has one stock record storing available quantity and warehouse location.

### `orders` -> `order_items`

Relationship:

- One-to-many

Meaning:

- One order can contain multiple items.
- Each order item belongs to exactly one order.

Implemented by:

- `order_items.order_id` foreign key to `orders.id`

Example:

- One order can contain `SKU-100 x 2` and `SKU-200 x 1`.

### `products` -> `order_items`

Relationship:

- One-to-many

Meaning:

- One product can appear in many different order items across many orders.
- Each order item references one product.

Implemented by:

- `order_items.product_id` foreign key to `products.id`

Example:

- Product `SKU-100` can appear in hundreds of customer orders over time.

### `orders` -> `back_orders`

Relationship:

- One-to-many

Meaning:

- One order can have zero or more backorder rows.
- Each backorder row belongs to exactly one order.

Implemented by:

- `back_orders.order_id` foreign key to `orders.id`

### `products` -> `back_orders`

Relationship:

- One-to-many

Meaning:

- One product can appear in many backorder rows across orders.
- Each backorder row references one product.

Implemented by:

- `back_orders.product_id` foreign key to `products.id`

## End-to-end data flow

### Admin login flow

- Admin credentials are checked against `admin_users`.
- On success, Spring Security creates an HTTP session.
- No JWT token is generated or stored.

### Client creation flow

- Admin creates a client from the UI or API.
- A new row is inserted into `clients`.

### Product creation flow

- Admin creates a product and chooses a client.
- A new row is inserted into `products`.
- A corresponding inventory row is inserted into `inventory`.
- Serialized inventory units are inserted into `inventory_units` with status `BIN`.
- Existing product name, SKU, or UPC values do not block product creation.
- If the SKU already exists, new serial numbers continue from the existing SKU serial sequence.

### Order creation flow

- Customer places an order using product SKU and quantity.
- The application checks address serviceability.
- The application checks current available stock and serialized `BIN` inventory.
- A new row is inserted into `orders`.
- One or more rows are inserted into `order_items`.
- If stock is fully available:
  - stock values in `inventory` are updated for the full requested quantity
  - matching rows in `inventory_units` move from `BIN` to `ALLOCATED`
  - order status becomes `ALLOCATED`
- If stock is only partially available:
  - stock values in `inventory` are updated only for the allocatable quantity
  - matching rows in `inventory_units` move from `BIN` to `ALLOCATED`
  - remaining quantity is stored in `back_orders`
  - order status becomes `BACKORDERED`
- If address validation fails:
  - no order row is saved
  - no order item row is saved
  - inventory remains unchanged

### Backorder fulfillment flow

- A scheduled job runs every 5 minutes.
- The job reads orders in `BACKORDERED` status in created-time order.
- For each pending line, it checks newly available stock and `BIN` serial inventory.
- When stock is found, the job allocates those serialized units to the existing order.
- When all backordered quantities are fulfilled, the order status changes from `BACKORDERED` to `ALLOCATED`.

### Picker picking flow

- Order created
- Inventory allocated
- Picker submits `POST /api/orders/{orderNumber}/pick`
- Picker is assigned to the order
- Requested serials are validated against the order allocation
- Items are moved from allocated bin stock to the target container/tote
- Order status is updated to `READY_TO_PACK`

### Shipping packing flow

- Shipping submits `POST /api/orders/{orderNumber}/pack`
- Product weight is entered for the ready-to-pack order
- The system assigns a box based on the entered weight
- Package weight and assigned box are saved on the order
- Order status is updated to `PACKED`

### Shipping label, LPN, and truckload flow

- Shipping submits `POST /api/orders/{orderNumber}/shipping-label`
- The system generates a carrier-style PNG label image with tracking number, service level, ship-from, ship-to, package details, and barcode text
- For pallet/container shipment, shipping creates an LPN with `POST /api/lpns`
- Shipping attaches one or more orders with `POST /api/lpns/{lpnNumber}/orders`
- Shipping assigns an LPN carrier with `POST /api/lpns/{lpnNumber}/carrier`
- Shipping creates a truckload with `POST /api/truckloads`
- Shipping attaches carrier-assigned LPNs with `POST /api/truckloads/{truckloadNumber}/lpns`
- For direct shipment without LPN, shipping attaches orders with `POST /api/truckloads/{truckloadNumber}/orders`
- Shipping assigns the truckload carrier with `POST /api/truckloads/{truckloadNumber}/carrier`
- Shipping dispatches with `POST /api/truckloads/{truckloadNumber}/dispatch`
- Dispatch marks the truckload, attached LPNs, and all attached orders as `SHIPPED`
- The shipping table shows order info, label data, carrier data, and destination address
- After an order is `SHIPPED`, carrier APIs can send tracking progress into `POST /api/carrier`
- The carrier scheduler updates the order from `carrier_sent_data` using `carrier_status_mapping`

Order status flow:

- Full allocation: `CREATED` -> `ALLOCATED` -> `READY_TO_PACK` -> `PACKED` -> `READY_TO_SHIP` -> `SHIPPED`
- Partial allocation: `CREATED` -> `BACKORDERED` -> `ALLOCATED` -> `READY_TO_PACK` -> `PACKED` -> `READY_TO_SHIP` -> `SHIPPED`
- Carrier updates after shipment: `SHIPPED` -> `IN_TRANSIT` -> `OUT_FOR_DELIVERY` -> `DELIVERED`

Shipment status flow:

- LPN: `CREATED` -> `CARRIER_ASSIGNED` -> `SHIPPED`
- Truckload: `CREATED` -> `CARRIER_ASSIGNED` -> `SHIPPED`

Implementation note:

- The service sets `PICK_IN_PROGRESS` internally during picking, but the same request completes the pick and returns `READY_TO_PACK`.

Inventory movement flow:

- `BIN` -> `ALLOCATED` -> `CONTAINER`

## Logical ER view

You can think about the schema like this:

```text
admin_users
    |
    | session-based admin authentication
    |

clients (1) --------- (many) products (1) --------- (1) inventory
                                   |
                                   +--------- (many) order_items (many) --------- (1) orders
                                   |
                                   +--------- (many) back_orders (many) --------- (1) orders

orders (many) --------- (1) lpns (many) --------- (1) truckloads
orders (many) ------------------------------- (1) truckloads
```

More directly:

```text
clients.id --------< products.client_id
products.id ------- inventory.product_id
orders.id ---------< order_items.order_id
products.id -------< order_items.product_id
orders.id ---------< back_orders.order_id
products.id -------< back_orders.product_id
lpns.id -----------< orders.lpn_id
truckloads.id -----< lpns.truckload_id
truckloads.id -----< orders.direct_truckload_id
carrier_status_mapping.carrier_status --- used by scheduler for carrier_sent_data.carrier_status
carrier_sent_data.shipment_id ----------- matched to orders.shipment_id / orders.tracking_number / orders.order_number
```

## Approximate MySQL DDL reference

This project currently relies on Hibernate auto-generation, but the tables are conceptually equivalent to the following MySQL schema:

```sql
CREATE TABLE admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL
);

CREATE TABLE clients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    client_code VARCHAR(50) NOT NULL UNIQUE,
    contact_email VARCHAR(120) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL
);

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    client_id BIGINT NOT NULL,
    sku VARCHAR(80) NOT NULL,
    product_description VARCHAR(1000) NOT NULL,
    upc VARCHAR(80) NOT NULL,
    weight DECIMAL(10,3) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_products_client
        FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,
    warehouse_location VARCHAR(80) NOT NULL,
    CONSTRAINT fk_inventory_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    customer_name VARCHAR(120) NOT NULL,
    customer_email VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_picker VARCHAR(80),
    pick_container VARCHAR(80),
    package_weight DECIMAL(10,3),
    assigned_box VARCHAR(80),
    shipment_id VARCHAR(80),
    tracking_number VARCHAR(80),
    shipment_status VARCHAR(60),
    order_status_code INT,
    updated_at TIMESTAMP,
    lpn_id BIGINT,
    direct_truckload_id BIGINT,
    serviceable BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL
);

CREATE TABLE shipped_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    shipping_label_number VARCHAR(80) NOT NULL,
    shipping_label_generated_at TIMESTAMP NOT NULL,
    tracking_number VARCHAR(80) NOT NULL,
    carrier VARCHAR(80) NOT NULL,
    service_level VARCHAR(80) NOT NULL,
    CONSTRAINT fk_shipped_data_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE TABLE truckloads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    truckload_number VARCHAR(50) NOT NULL UNIQUE,
    carrier_name VARCHAR(80),
    dispatch_date TIMESTAMP,
    shipment_status VARCHAR(30) NOT NULL,
    remarks VARCHAR(500)
);

CREATE TABLE lpns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lpn_number VARCHAR(40) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(80) NOT NULL,
    carrier_name VARCHAR(80),
    shipment_status VARCHAR(30) NOT NULL,
    total_orders INT NOT NULL,
    total_quantity INT NOT NULL,
    total_weight DECIMAL(12,3) NOT NULL,
    remarks VARCHAR(500),
    truckload_id BIGINT,
    CONSTRAINT fk_lpns_truckload
        FOREIGN KEY (truckload_id) REFERENCES truckloads(id)
);

CREATE TABLE lpn_sequences (
    sequence_date DATE PRIMARY KEY,
    next_value INT NOT NULL
);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_lpn
        FOREIGN KEY (lpn_id) REFERENCES lpns(id),
    ADD CONSTRAINT fk_orders_direct_truckload
        FOREIGN KEY (direct_truckload_id) REFERENCES truckloads(id);

CREATE TABLE inventory_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_id BIGINT NOT NULL,
    allocated_order_id BIGINT NULL,
    serial_number VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    current_location VARCHAR(80) NOT NULL,
    container_location VARCHAR(80) NULL
);

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE back_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    pending_quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_back_orders_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_back_orders_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE carrier_status_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    carrier_status VARCHAR(60) NOT NULL UNIQUE,
    status_code INT NOT NULL
);

CREATE TABLE carrier_sent_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id VARCHAR(80) NOT NULL,
    carrier_status VARCHAR(60) NOT NULL,
    webhook INT NOT NULL DEFAULT 0,
    status_time TIMESTAMP NULL
);
```

## Validation rules

- Address is serviceable only when mandatory fields are present and postal code is a 5-6 digit value not starting with `999`
- Product creation allows repeated product name, SKU, and UPC values
- Inventory allocation checks requested quantity against both available stock and serialized `BIN` units
- If stock is partial, the order is still created and the remainder is moved to backorder
- If stock is available later, the scheduler allocates the backordered quantity automatically
- Shipped, cancelled, and rejected orders cannot be added to LPNs or truckloads
- Orders inside an LPN cannot be attached directly to a truckload
- Orders already attached directly to a truckload cannot be added to an LPN
- LPNs require a carrier before they can be attached to a truckload
- Truckloads require a carrier before dispatch
- Dispatching a truckload marks the truckload, attached LPNs, and all attached orders as `SHIPPED`
- Carrier updates must include `shipment_id` and `carrier_status`
- Carrier statuses must exist in `carrier_status_mapping` before the scheduler can apply them
- Unknown carrier statuses are skipped and logged
- If validation fails, the API returns a clear error response
