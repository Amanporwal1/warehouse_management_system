const state = {
    authenticated: false,
    username: null,
    transactionOrders: [],
    shippingOrders: [],
    lastLpnNumber: null,
    lastTruckloadNumber: null,
    orderSearch: "",
    statusFilters: new Set()
};

const sessionBadge = document.getElementById("sessionBadge");
const authMessage = document.getElementById("authMessage");
const clientMessage = document.getElementById("clientMessage");
const productMessage = document.getElementById("productMessage");
const orderMessage = document.getElementById("orderMessage");
const pickMessage = document.getElementById("pickMessage");
const packMessage = document.getElementById("packMessage");
const shippingMessage = document.getElementById("shippingMessage");
const labelPreview = document.getElementById("labelPreview");
const clientsList = document.getElementById("clientsList");
const productsList = document.getElementById("productsList");
const inventoryList = document.getElementById("inventoryList");
const transactionOrdersList = document.getElementById("transactionOrdersList");
const lpnSelectableOrdersList = document.getElementById("lpnSelectableOrdersList");
const pageTitle = document.getElementById("pageTitle");
const breadcrumb = document.getElementById("breadcrumb");

const pageMeta = {
    orders: ["MANAGE ORDERS", "Home / Fulfillment / Order / <strong>Manage Orders</strong>"],
    shipping: ["SHIPPING WORKFLOW", "Home / Fulfillment / <strong>Shipping</strong>"],
    lpn: ["LPN MANAGEMENT", "Home / Fulfillment / <strong>LPN</strong>"],
    truckload: ["TRUCKLOAD MANAGEMENT", "Home / Fulfillment / <strong>Truckload</strong>"],
    master: ["MASTER DATA", "Home / Administration / <strong>Master Data</strong>"],
    inventory: ["INVENTORY", "Home / Warehouse / <strong>Inventory</strong>"]
};

document.getElementById("searchForm").addEventListener("submit", onSearchOrders);
document.querySelectorAll(".rail-item[data-page]").forEach(button => {
    button.addEventListener("click", () => showPage(button.dataset.page));
});
document.querySelectorAll("[data-page-link]").forEach(button => {
    button.addEventListener("click", () => showPage(button.dataset.pageLink));
});
document.getElementById("loginForm").addEventListener("submit", onLogin);
document.getElementById("logoutButton").addEventListener("click", onLogout);
document.getElementById("clientForm").addEventListener("submit", onCreateClient);
document.getElementById("productForm").addEventListener("submit", onCreateProduct);
document.getElementById("orderForm").addEventListener("submit", onCreateOrder);
document.getElementById("pickForm").addEventListener("submit", onPickOrder);
document.getElementById("packForm").addEventListener("submit", onPackOrder);
document.getElementById("labelForm").addEventListener("submit", onGenerateShippingLabel);
document.getElementById("lpnForm").addEventListener("submit", onCreateLpn);
document.getElementById("lpnOrdersForm").addEventListener("submit", onAttachOrdersToLpn);
document.getElementById("lpnCarrierForm").addEventListener("submit", onAssignLpnCarrier);
document.getElementById("truckloadForm").addEventListener("submit", onCreateTruckload);
document.getElementById("truckloadLpnsForm").addEventListener("submit", onAttachLpnsToTruckload);
document.getElementById("truckloadOrdersForm").addEventListener("submit", onAttachOrdersToTruckload);
document.getElementById("truckloadCarrierForm").addEventListener("submit", onAssignTruckloadCarrier);
document.getElementById("truckloadDispatchForm").addEventListener("submit", onDispatchTruckload);
document.getElementById("refreshClients").addEventListener("click", loadClients);
document.getElementById("refreshProducts").addEventListener("click", loadProducts);
document.getElementById("refreshInventory").addEventListener("click", loadInventory);
document.getElementById("refreshShippingOrders").addEventListener("click", loadShippingOrders);
document.getElementById("refreshLpnOrders").addEventListener("click", loadShippingOrders);
document.querySelectorAll(".status-filters input").forEach(input => {
    input.addEventListener("change", onStatusFilterChange);
});

boot();

async function boot() {
    await checkSession();
    await loadOrderTransactions();
    await loadShippingOrders();
    if (state.authenticated) {
        await Promise.all([loadClients(), loadProducts(), loadInventory()]);
    }
}

function showPage(page) {
    document.querySelectorAll(".rail-item[data-page]").forEach(button => {
        button.classList.toggle("active", button.dataset.page === page);
    });
    document.querySelectorAll(".module-page").forEach(panel => {
        panel.classList.toggle("active", panel.dataset.pagePanel === page);
    });

    const [title, crumb] = pageMeta[page] || pageMeta.orders;
    pageTitle.textContent = title;
    breadcrumb.innerHTML = crumb;

    if (page === "orders" || page === "lpn") {
        loadOrderTransactions();
        loadShippingOrders();
    }
    if (page === "inventory" && state.authenticated) {
        Promise.all([loadClients(), loadProducts(), loadInventory()]);
    }
}

function onSearchOrders(event) {
    event.preventDefault();
    state.orderSearch = event.currentTarget.query.value.trim().toLowerCase();
    renderTransactionOrders(state.transactionOrders);
}

function onStatusFilterChange(event) {
    if (event.currentTarget.checked) {
        state.statusFilters.add(event.currentTarget.value);
    } else {
        state.statusFilters.delete(event.currentTarget.value);
    }
    renderTransactionOrders(state.transactionOrders);
}

async function onLogin(event) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = Object.fromEntries(formData.entries());

    try {
        const data = await requestJson("/api/auth/login", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        state.authenticated = true;
        state.username = data.username;
        setSessionUi();
        setMessage(authMessage, `Logged in as ${data.username}. Admin panels are ready.`, "success");
        await Promise.all([loadClients(), loadProducts(), loadInventory()]);
    } catch (error) {
        state.authenticated = false;
        state.username = null;
        setSessionUi();
        setMessage(authMessage, error.message, "error");
    }
}

async function onLogout() {
    try {
        await requestJson("/api/auth/logout", { method: "POST" });
    } catch (error) {
        setMessage(authMessage, error.message, "error");
    } finally {
        state.authenticated = false;
        state.username = null;
        setSessionUi();
        clearAdminData();
        setMessage(authMessage, "Logged out. Admin panels are locked again.", "info");
    }
}

async function checkSession() {
    try {
        const data = await requestJson("/api/auth/me");
        state.authenticated = true;
        state.username = data.username;
        setSessionUi();
        setMessage(authMessage, `Active session found for ${data.username}.`, "success");
    } catch (error) {
        state.authenticated = false;
        state.username = null;
        setSessionUi();
        setMessage(authMessage, "No admin session yet. You can still place public orders.", "info");
    }
}

async function onCreateClient(event) {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.currentTarget).entries());

    try {
        const data = await requestJson("/api/admin/clients", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(clientMessage, `Client created: ${data.name} (${data.clientCode})`, "success");
        event.currentTarget.reset();
        await loadClients();
    } catch (error) {
        setMessage(clientMessage, error.message, "error");
    }
}

async function onCreateProduct(event) {
    event.preventDefault();
    const payload = formToObject(event.currentTarget, [
        "weight",
        "unitPrice",
        "openingStock"
    ]);

    try {
        const data = await requestJson("/api/admin/products", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(productMessage, `Product created: ${data.productName} with SKU ${data.sku}`, "success");
        event.currentTarget.reset();
        await Promise.all([loadProducts(), loadInventory()]);
    } catch (error) {
        setMessage(productMessage, error.message, "error");
    }
}

async function onCreateOrder(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        customerName: form.customerName.value.trim(),
        customerEmail: form.customerEmail.value.trim(),
        shippingAddress: {
            addressLine1: form.addressLine1.value.trim(),
            addressLine2: form.addressLine2.value.trim(),
            city: form.city.value.trim(),
            state: form.state.value.trim(),
            postalCode: form.postalCode.value.trim(),
            country: form.country.value.trim()
        },
        items: [
            {
                sku: form.sku.value.trim(),
                quantity: Number(form.quantity.value)
            }
        ]
    };

    try {
        const data = await requestJson("/api/orders", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            orderMessage,
            `Order ${data.orderNumber} moved to ${data.status}. ${data.message}`,
            "success"
        );
        form.reset();
        if (state.authenticated) {
            await loadInventory();
        }
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(orderMessage, error.message, "error");
    }
}

async function onPickOrder(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const orderNumber = form.orderNumber.value.trim();
    const payload = {
        pickerId: form.pickerId.value.trim(),
        containerId: form.containerId.value.trim(),
        serialNumbers: parseSerialNumbers(form.serialNumbers.value)
    };

    try {
        const data = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}/pick`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        const serialSummary = data.pickedInventory.map(item => item.serialNumber).join(", ");
        setMessage(
            pickMessage,
            `Order ${data.orderNumber} assigned to ${data.pickerId} and moved to ${data.status}. Container ${data.containerId}. Serials: ${serialSummary}`,
            "success"
        );
        form.reset();
        if (state.authenticated) {
            await loadInventory();
        }
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(pickMessage, error.message, "error");
    }
}

async function onPackOrder(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const orderNumber = form.orderNumber.value.trim();
    const payload = {
        weight: Number(form.weight.value)
    };

    try {
        const data = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}/pack`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            packMessage,
            `Order ${data.orderNumber} assigned ${data.assignedBox} for ${data.weight} weight and moved to ${data.status}.`,
            "success"
        );
        form.reset();
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(packMessage, error.message, "error");
    }
}

async function onGenerateShippingLabel(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const orderNumber = form.orderNumber.value.trim();

    try {
        const data = await requestJson(`/api/orders/${encodeURIComponent(orderNumber)}/shipping-label`, {
            method: "POST"
        });
        setMessage(
            shippingMessage,
            `Order ${data.orderNumber} label ${data.shippingLabelNumber} generated with tracking ${data.trackingNumber} via ${data.carrier} ${data.serviceLevel}.`,
            "success"
        );
        renderLabelPreview(data);
        form.reset();
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onCreateLpn(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        createdBy: form.createdBy.value.trim(),
        remarks: form.remarks.value.trim()
    };

    try {
        const data = await requestJson("/api/lpns", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `LPN ${data.lpnNumber} created with status ${data.shipmentStatus}.`,
            "success"
        );
        state.lastLpnNumber = data.lpnNumber;
        fillInputs("lpnNumber", data.lpnNumber);
        form.reset();
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onAttachOrdersToLpn(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const lpnNumber = form.lpnNumber.value.trim();
    const selectedOrderNumbers = getSelectedLpnOrderNumbers();

    if (!selectedOrderNumbers.length) {
        setMessage(shippingMessage, "Select at least one order to attach to the LPN.", "error");
        return;
    }

    const payload = {
        orderNumbers: selectedOrderNumbers
    };

    try {
        const data = await requestJson(`/api/lpns/${encodeURIComponent(lpnNumber)}/orders`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `LPN ${data.lpnNumber} now has ${data.totalOrders} orders and ${data.totalQuantity} units.`,
            "success"
        );
        form.reset();
        clearSelectedLpnOrders();
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onAssignLpnCarrier(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const lpnNumber = form.lpnNumber.value.trim();
    const payload = {
        carrierName: form.carrierName.value.trim()
    };

    try {
        const data = await requestJson(`/api/lpns/${encodeURIComponent(lpnNumber)}/carrier`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `Carrier ${data.carrierName} assigned to LPN ${data.lpnNumber}.`,
            "success"
        );
        state.lastLpnNumber = data.lpnNumber;
        fillTextarea("lpnNumbers", data.lpnNumber);
        form.reset();
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onCreateTruckload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const payload = {
        remarks: form.remarks.value.trim()
    };

    try {
        const data = await requestJson("/api/truckloads", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `Truckload ${data.truckloadNumber} created with status ${data.shipmentStatus}.`,
            "success"
        );
        state.lastTruckloadNumber = data.truckloadNumber;
        fillInputs("truckloadNumber", data.truckloadNumber);
        form.reset();
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onAttachLpnsToTruckload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const truckloadNumber = form.truckloadNumber.value.trim();
    const payload = {
        lpnNumbers: parseLineList(form.lpnNumbers.value)
    };

    try {
        const data = await requestJson(`/api/truckloads/${encodeURIComponent(truckloadNumber)}/lpns`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `Truckload ${data.truckloadNumber} now has ${data.lpnNumbers.length} LPNs.`,
            "success"
        );
        state.lastTruckloadNumber = data.truckloadNumber;
        fillInputs("truckloadNumber", data.truckloadNumber);
        form.reset();
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onAttachOrdersToTruckload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const truckloadNumber = form.truckloadNumber.value.trim();
    const payload = {
        orderNumbers: parseLineList(form.orderNumbers.value)
    };

    try {
        const data = await requestJson(`/api/truckloads/${encodeURIComponent(truckloadNumber)}/orders`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `Truckload ${data.truckloadNumber} now has ${data.directOrderNumbers.length} direct orders.`,
            "success"
        );
        state.lastTruckloadNumber = data.truckloadNumber;
        fillInputs("truckloadNumber", data.truckloadNumber);
        form.reset();
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onAssignTruckloadCarrier(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const truckloadNumber = form.truckloadNumber.value.trim();
    const payload = {
        carrierName: form.carrierName.value.trim()
    };

    try {
        const data = await requestJson(`/api/truckloads/${encodeURIComponent(truckloadNumber)}/carrier`, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        setMessage(
            shippingMessage,
            `Carrier ${data.carrierName} assigned to truckload ${data.truckloadNumber}.`,
            "success"
        );
        state.lastTruckloadNumber = data.truckloadNumber;
        form.reset();
        fillInputs("truckloadNumber", data.truckloadNumber);
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function onDispatchTruckload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const truckloadNumber = form.truckloadNumber.value.trim();

    try {
        const data = await requestJson(`/api/truckloads/${encodeURIComponent(truckloadNumber)}/dispatch`, {
            method: "POST"
        });
        setMessage(
            shippingMessage,
            `Truckload ${data.truckloadNumber} dispatched. ${data.lpnNumbers.length} LPNs and ${data.directOrderNumbers.length} direct orders shipped.`,
            "success"
        );
        form.reset();
        await Promise.all([loadOrderTransactions(), loadShippingOrders()]);
    } catch (error) {
        setMessage(shippingMessage, error.message, "error");
    }
}

async function loadOrderTransactions() {
    try {
        const orders = await requestJson("/api/orders/transactions");
        state.transactionOrders = orders;
        renderTransactionOrders(orders);
    } catch (error) {
        state.transactionOrders = [];
        transactionOrdersList.innerHTML = `<tr><td colspan="13" class="empty-row">${escapeHtml(error.message)}</td></tr>`;
    }
}

async function loadClients() {
    if (!state.authenticated) {
        clientsList.className = "data-list empty-state";
        clientsList.textContent = "Login to load clients.";
        return;
    }

    try {
        const clients = await requestJson("/api/admin/clients");
        renderCards(clientsList, clients, client => `
            <strong>${escapeHtml(client.name)}</strong>
            <p>Code: ${escapeHtml(client.clientCode)}</p>
            <p>${escapeHtml(client.contactEmail)} · ${escapeHtml(client.contactPhone)}</p>
        `, "No clients created yet.");
    } catch (error) {
        setLoadError(clientsList, error.message);
    }
}

async function loadProducts() {
    if (!state.authenticated) {
        productsList.className = "data-list empty-state";
        productsList.textContent = "Login to load products.";
        return;
    }

    try {
        const products = await requestJson("/api/admin/products");
        renderCards(productsList, products, product => `
            <strong>${escapeHtml(product.productName)}</strong>
            <p>SKU: ${escapeHtml(product.sku)} · Client: ${escapeHtml(product.clientName)}</p>
            <p>UPC: ${escapeHtml(product.upc)} · Stock: ${product.availableStock}</p>
            <p>Price: ${product.unitPrice} · Weight: ${product.weight}</p>
        `, "No products created yet.");
    } catch (error) {
        setLoadError(productsList, error.message);
    }
}

async function loadInventory() {
    if (!state.authenticated) {
        inventoryList.className = "data-list empty-state";
        inventoryList.textContent = "Login to load inventory.";
        return;
    }

    try {
        const inventory = await requestJson("/api/admin/inventory");
        renderInventoryRows(inventory);
    } catch (error) {
        setLoadError(inventoryList, error.message);
    }
}

async function loadShippingOrders() {
    try {
        const orders = await requestJson("/api/orders/shipping-table");
        state.shippingOrders = orders;
        renderLpnSelectableOrders(orders);
    } catch (error) {
        state.shippingOrders = [];
        setLoadError(lpnSelectableOrdersList, error.message);
    }
}

function renderTransactionOrders(orders) {
    const filteredOrders = orders.filter(order => {
        const matchesSearch = !state.orderSearch
            || String(order.orderNumber || "").toLowerCase().includes(state.orderSearch)
            || String(order.shippingLabelNumber || "").toLowerCase().includes(state.orderSearch)
            || String(order.trackingNumber || "").toLowerCase().includes(state.orderSearch);
        const matchesStatus = !state.statusFilters.size || state.statusFilters.has(order.status);
        return matchesSearch && matchesStatus;
    });

    if (!filteredOrders.length) {
        transactionOrdersList.innerHTML = '<tr><td colspan="13" class="empty-row">No orders match the current view.</td></tr>';
        return;
    }

    transactionOrdersList.innerHTML = filteredOrders.map(order => `
        <tr>
            <td class="select-col"><span class="row-radio"></span></td>
            <td>
                <a class="order-link" href="#">${escapeHtml(order.orderNumber)}</a><span class="info-dot">i</span>
            </td>
            <td>${escapeHtml(formatDateTime(order.createdAt) || "Pending")}</td>
            <td><span class="status-badge status-${escapeHtml(String(order.status || "").toLowerCase())}">${formatOrderStatus(order.status)}</span></td>
            <td>${escapeHtml(order.customerName || "OMS Client")}<br><span class="table-subtext">${escapeHtml(order.customerEmail || "")}</span></td>
            <td>${escapeHtml(order.itemSummary || "Pending")}<br><span class="table-subtext">Qty: ${escapeHtml(order.totalItemQuantity ?? 0)}</span></td>
            <td>${escapeHtml(order.backorderedQuantity ?? 0)}</td>
            <td>${escapeHtml(order.assignedPicker || "Pending")}<br><span class="table-subtext">${escapeHtml(order.pickContainer || "Pending")}</span></td>
            <td>${escapeHtml(order.assignedBox || "Pending")}<br><span class="table-subtext">${escapeHtml(formatWeight(order.packageWeight))}</span></td>
            <td>${escapeHtml(order.shipmentId || "Pending")}<br><span class="table-subtext">${escapeHtml(order.trackingNumber || order.shippingLabelNumber || "Pending")}</span></td>
            <td>${escapeHtml(order.shippingCarrier || order.lpnCarrier || order.truckloadCarrier || "Pending")}<br><span class="table-subtext">${escapeHtml(order.shipmentStatus || order.serviceLevel || "Pending")}</span></td>
            <td>${escapeHtml(order.lpnNumber || "None")}<br><span class="table-subtext">${escapeHtml(order.lpnShipmentStatus || "None")}</span></td>
            <td>${escapeHtml(order.truckloadNumber || "None")}<br><span class="table-subtext">${escapeHtml(order.truckloadShipmentStatus || "None")}</span></td>
            <td>${escapeHtml(formatDateTime(order.deliveredAt || order.lastCarrierUpdateAt) || "Pending")}</td>
        </tr>
    `).join("");
}

function renderLpnSelectableOrders(orders) {
    const visibleOrders = orders.filter(order => order.status !== "SHIPPED");

    if (!visibleOrders.length) {
        lpnSelectableOrdersList.className = "selectable-list empty-state";
        lpnSelectableOrdersList.textContent = "No orders are available for LPN attachment.";
        return;
    }

    lpnSelectableOrdersList.className = "selectable-list";
    lpnSelectableOrdersList.innerHTML = visibleOrders.map((order, index) => {
        const alreadyGrouped = Boolean(order.lpnNumber || order.truckloadNumber);
        return `
        <label class="selectable-order ${alreadyGrouped ? "selectable-order-disabled" : ""}" style="animation-delay:${index * 60}ms">
            <input type="checkbox" name="lpnOrderNumber" value="${escapeHtml(order.orderNumber)}" ${alreadyGrouped ? "disabled" : ""}>
            <span class="selectable-order-body">
                <strong>${escapeHtml(order.orderNumber)} &middot; ${escapeHtml(order.status)}</strong>
                <span>Customer: ${escapeHtml(order.customerName)} &middot; ${escapeHtml(order.customerEmail)}</span>
                <span>Box: ${escapeHtml(order.assignedBox || "Pending")} &middot; Label: ${escapeHtml(order.shippingLabelNumber || "Pending")}</span>
                <span>Carrier: ${escapeHtml(order.carrier || "Pending")} &middot; Tracking: ${escapeHtml(order.trackingNumber || "Pending")}</span>
                <span>LPN: ${escapeHtml(order.lpnNumber || "None")} &middot; Truckload: ${escapeHtml(order.truckloadNumber || "None")}</span>
                <span>Destination: ${escapeHtml(order.destinationAddress)}</span>
            </span>
        </label>
    `;
    }).join("");
}

function getSelectedLpnOrderNumbers() {
    return Array.from(lpnSelectableOrdersList.querySelectorAll('input[name="lpnOrderNumber"]:checked'))
        .map(input => input.value);
}

function clearSelectedLpnOrders() {
    lpnSelectableOrdersList.querySelectorAll('input[name="lpnOrderNumber"]:checked')
        .forEach(input => {
            input.checked = false;
        });
}

function formatOrderStatus(status) {
    return String(status || "UNKNOWN")
        .replaceAll("_", "-");
}

function formatDateTime(value) {
    if (!value) {
        return "";
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return String(value);
    }
    return parsed.toLocaleString("en-IN", {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function formatWeight(value) {
    if (value === null || value === undefined || value === "") {
        return "Pending";
    }
    return `${value} kg`;
}

function fillInputs(name, value) {
    document.querySelectorAll(`input[name="${name}"]`).forEach(input => {
        input.value = value;
    });
}

function fillTextarea(name, value) {
    document.querySelectorAll(`textarea[name="${name}"]`).forEach(textarea => {
        textarea.value = value;
    });
}

function renderLabelPreview(label) {
    labelPreview.className = "label-preview";
    labelPreview.innerHTML = `
        <img src="${escapeHtml(label.labelImageDataUrl)}" alt="Shipping label ${escapeHtml(label.shippingLabelNumber)}">
        <a class="label-download" href="${escapeHtml(label.labelImageDataUrl)}" download="${escapeHtml(label.shippingLabelNumber)}.png">Download PNG</a>
    `;
}

function renderCards(container, items, template, emptyMessage) {
    if (!items.length) {
        container.className = "data-list empty-state";
        container.textContent = emptyMessage;
        return;
    }

    container.className = "data-list";
    container.innerHTML = items.map((item, index) =>
        `<article class="data-card" style="animation-delay:${index * 60}ms">${template(item)}</article>`
    ).join("");
}

function renderInventoryRows(items) {
    if (!items.length) {
        inventoryList.className = "inventory-table empty-state";
        inventoryList.textContent = "No inventory available yet.";
        return;
    }

    inventoryList.className = "inventory-table";
    inventoryList.innerHTML = `
        <div class="inventory-row inventory-head">
            <span>SKU</span>
            <span>Available</span>
            <span>Reserved</span>
            <span>Warehouse</span>
        </div>
        ${items.map(item => `
            <div class="inventory-row">
                <strong>${escapeHtml(item.sku)}</strong>
                <span>${item.availableQuantity}</span>
                <span>${item.reservedQuantity}</span>
                <span>${escapeHtml(item.warehouseLocation)}</span>
            </div>
        `).join("")}
    `;
}

function clearAdminData() {
    clientsList.className = "data-list empty-state";
    clientsList.textContent = "Login to load clients.";
    productsList.className = "data-list empty-state";
    productsList.textContent = "Login to load products.";
    inventoryList.className = "data-list empty-state";
    inventoryList.textContent = "Login to load inventory.";
}

function setSessionUi() {
    const adminOnlyElements = document.querySelectorAll(".admin-only");
    adminOnlyElements.forEach(element => {
        if ("disabled" in element) {
            element.disabled = !state.authenticated;
        }
        element.querySelectorAll?.("input, textarea, button").forEach(child => {
            child.disabled = !state.authenticated;
        });
    });

    if (state.authenticated) {
        if (sessionBadge) {
            sessionBadge.textContent = `Admin ${state.username}`;
            sessionBadge.className = "chip-button status-auth";
        }
    } else {
        if (sessionBadge) {
            sessionBadge.textContent = "Guest mode";
            sessionBadge.className = "chip-button status-idle";
        }
    }
}

function setMessage(element, text, tone) {
    if (!text) {
        element.textContent = "";
        element.classList.add("is-hidden");
        return;
    }

    element.classList.remove("is-hidden");
    element.textContent = text;
    element.style.borderColor = tone === "error"
        ? "rgba(169, 60, 47, 0.35)"
        : tone === "success"
            ? "rgba(31, 122, 79, 0.35)"
            : "rgba(85, 56, 34, 0.24)";
    element.style.color = tone === "error"
        ? "var(--danger)"
        : tone === "success"
            ? "var(--success)"
            : "var(--muted)";
}

function setLoadError(container, message) {
    container.className = "data-list empty-state";
    container.textContent = message;
}

function formToObject(form, numericFields) {
    const data = Object.fromEntries(new FormData(form).entries());
    numericFields.forEach(field => {
        data[field] = Number(data[field]);
    });
    return data;
}

function parseSerialNumbers(value) {
    return value
        .split(/\r?\n|,/)
        .map(normalizeSerialNumber)
        .filter(Boolean);
}

function parseLineList(value) {
    return String(value ?? "")
        .split(/\r?\n|,/)
        .map(item => item.trim())
        .filter(Boolean);
}

function normalizeSerialNumber(value) {
    return String(value ?? "")
        .trim()
        .replace(/^['"]+/, "")
        .replace(/['",]+$/, "")
        .trim();
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, {
        headers: options.body ? {
            "Content-Type": "application/json",
            ...(options.headers || {})
        } : {
            ...(options.headers || {})
        },
        credentials: "same-origin",
        ...options
    });

    const text = await response.text();
    let data = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch (error) {
            data = { message: text };
        }
    }

    if (!response.ok) {
        throw new Error(data.message || data.error || "Request failed");
    }

    return data;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
