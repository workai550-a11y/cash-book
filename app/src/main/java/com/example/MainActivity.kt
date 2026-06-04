package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.BudgetLimit
import com.example.model.SavingsGoal
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.repository.FinanceRepository
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Modern Fintech Palette Setup
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate400 = Color(0xFF94A3B8)
val Slate50 = Color(0xFFF8FAFC)

val EmeraldPrimary = Color(0xFF10B981) // High-contrast mint/emerald
val EmeraldMuted = Color(0x2210B981)
val RoseExpense = Color(0xFFF43F5E) // Modern pinkish red
val RoseMuted = Color(0x22F43F5E)
val AmberWarning = Color(0xFFF59E0B) // Warn for budgets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val viewModel = remember { FinanceViewModel(context) }
                MainFinancialAppScreen(viewModel)
            }
        }
    }
}

// Inline Lightweight viewmodel-like state managing pattern
class FinanceViewModel(context: android.content.Context) {
    private val repository = FinanceRepository(context)

    var transactions by mutableStateOf<List<Transaction>>(emptyList())
        private set

    var budgetLimits by mutableStateOf<List<BudgetLimit>>(emptyList())
        private set

    var savingsGoals by mutableStateOf<List<SavingsGoal>>(emptyList())
        private set

    init {
        loadData()
    }

    fun loadData() {
        transactions = repository.getTransactions().sortedByDescending { it.date }
        budgetLimits = repository.getBudgetLimits()
        savingsGoals = repository.getSavingsGoals()
    }

    fun addTransaction(tx: Transaction) {
        val newList = (transactions + tx).sortedByDescending { it.date }
        transactions = newList
        repository.saveTransactions(newList)
    }

    fun deleteTransaction(txId: String) {
        val newList = transactions.filter { it.id != txId }
        transactions = newList
        repository.saveTransactions(newList)
    }

    fun updateTransaction(tx: Transaction) {
        val newList = transactions.map { if (it.id == tx.id) tx else it }.sortedByDescending { it.date }
        transactions = newList
        repository.saveTransactions(newList)
    }

    fun updateBudgetLimit(category: String, amount: Double) {
        val filtered = budgetLimits.filter { it.category != category }
        val newList = if (amount > 0.0) filtered + BudgetLimit(category, amount) else filtered
        budgetLimits = newList
        repository.saveBudgetLimits(newList)
    }

    fun addSavingsGoal(goalName: String, target: Double, initial: Double) {
        val newList = savingsGoals + SavingsGoal(name = goalName, targetAmount = target, currentAmount = initial)
        savingsGoals = newList
        repository.saveSavingsGoals(newList)
    }

    fun contributeToSavingsGoal(goalId: String, amount: Double) {
        val oldGoal = savingsGoals.find { it.id == goalId } ?: return
        val newList = savingsGoals.map {
            if (it.id == goalId) {
                it.copy(currentAmount = (it.currentAmount + amount).coerceAtMost(it.targetAmount))
            } else {
                it
            }
        }
        savingsGoals = newList
        repository.saveSavingsGoals(newList)

        // Add auto expense transaction logger for savings
        addTransaction(
            Transaction(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = "Savings Transfer",
                description = "Saved towards: ${oldGoal.name}",
                note = "Autogenerated transfer to vault."
            )
        )
    }

    fun deleteSavingsGoal(goalId: String) {
        val newList = savingsGoals.filter { it.id != goalId }
        savingsGoals = newList
        repository.saveSavingsGoals(newList)
    }
}

// Primary Financial Application Composable
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainFinancialAppScreen(viewModel: FinanceViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Transactions, 2 = Budgets / Goals
    var showAddDialog by remember { mutableStateOf(false) }

    // Dialog state for editting
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Slate900,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Slate900,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Transaction", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // High-end app header
            HeaderPanel()

            // Main Core Balance Bento Card
            BalanceBentoCard(viewModel.transactions)

            // Segmented Sliders Row tabs
            TabSegmentedRow(selectedTab, onTabSelected = { selectedTab = it })

            HorizontalDivider(color = Slate800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

            // Body Area with fade-in transitions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        transactions = viewModel.transactions,
                        budgetLimits = viewModel.budgetLimits,
                        onQuickTransferGoal = { goalId -> 
                            // Quick transfer dialog can be invoked
                        }
                    )
                    1 -> TransactionsTab(
                        transactions = viewModel.transactions,
                        onEdit = { transactionToEdit = it },
                        onDelete = { viewModel.deleteTransaction(it) }
                    )
                    2 -> BudgetsAndGoalsTab(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Add and Edit dialogues rendering block
    if (showAddDialog) {
        TransactionEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { tx ->
                viewModel.addTransaction(tx)
                showAddDialog = false
            }
        )
    }

    if (transactionToEdit != null) {
        TransactionEntryDialog(
            transaction = transactionToEdit,
            onDismiss = { transactionToEdit = null },
            onSave = { updatedTx ->
                viewModel.updateTransaction(updatedTx)
                transactionToEdit = null
            }
        )
    }
}

@Composable
fun HeaderPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EmeraldMuted, shape = RoundedCornerShape(10.dp))
                    .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Wallet Vector Logo",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Personal Finance",
                    color = Slate50,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Smart Budget & Insights",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }
        }

        // Today format display
        val todayStr = remember {
            SimpleDateFormat("EEE, MMM dd", Locale.US).format(Date())
        }
        Text(
            text = todayStr,
            color = Slate400,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Slate800, shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun BalanceBentoCard(transactions: List<Transaction>) {
    val incomes = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netBalance = incomes - expenses

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "LIQUID POSITION WORTH",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatAmount(netBalance),
                color = if (netBalance >= 0) Slate50 else RoseExpense,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Income component
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Slate900.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(EmeraldMuted, shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Income Up Icon",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Total Income", color = Slate400, fontSize = 11.sp)
                            Text(
                                text = formatAmount(incomes),
                                color = EmeraldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Expense component
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Slate900.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(RoseMuted, shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Expense Down Icon",
                                tint = RoseExpense,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Total Outflows", color = Slate400, fontSize = 11.sp)
                            Text(
                                text = formatAmount(expenses),
                                color = RoseExpense,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabSegmentedRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val labels = listOf("Dashboard", "Transactions", "Vaults & Budgets")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Slate800, shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) EmeraldPrimary else Color.Transparent)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Slate900 else Slate400,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// --- TAB 1: DASHBOARD OVERVIEW ---
@Composable
fun DashboardTab(
    transactions: List<Transaction>,
    budgetLimits: List<BudgetLimit>,
    onQuickTransferGoal: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Visual Outflow Breakdown
        Text(
            text = "OUTLOOK BY CATEGORY",
            color = Slate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(16.dp)
        ) {
            FinanceDonutChart(transactions = transactions)
        }

        // Section: Current Budgets Status Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BUDGET HEALTH ALERTS",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "${budgetLimits.size} Configured",
                color = EmeraldPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (budgetLimits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate800, shape = RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No category budgets configured yet.\nActivate them in the 'Vaults & Budgets' panel.",
                    color = Slate400,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (limit in budgetLimits) {
                    val spent = transactions
                        .filter { it.type == TransactionType.EXPENSE && it.category == limit.category }
                        .sumOf { it.amount }
                    val progress = if (limit.amount > 0) (spent / limit.amount).toFloat() else 0f

                    val progressColor = when {
                        progress > 1.0f -> RoseExpense
                        progress > 0.8f -> AmberWarning
                        else -> EmeraldPrimary
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = limit.category,
                                    color = Slate50,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${formatAmount(spent)} of ${formatAmount(limit.amount)}",
                                    color = if (spent > limit.amount) RoseExpense else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress.coerceAtMost(1f),
                                color = progressColor,
                                trackColor = Slate900,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            if (spent > limit.amount) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Alert",
                                        tint = RoseExpense,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Limit exceeded by ${formatAmount(spent - limit.amount)}!",
                                        color = RoseExpense,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun FinanceDonutChart(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
    val totalExpense = expenseTransactions.sumOf { it.amount }

    val categoryTotals = expenseTransactions.groupBy { it.category }
        .mapValues { it.value.sumOf { it.amount } }

    val sortedCategories = categoryTotals.toList().sortedByDescending { it.second }

    val categoryColors = remember {
        mapOf(
            "Food & Dining" to Color(0xFFF59E0B), // Amber Warning-like
            "Transport" to Color(0xFF0369A1),     // Sky Blue
            "Shopping" to Color(0xFFEC4899),      // Hot Pink
            "Utilities" to Color(0xFF3B82F6),     // Blue
            "Entertainment" to Color(0xFF8B5CF6),  // Violet
            "Savings Transfer" to Color(0xFF14B8A6), // Green Teal
            "Others" to Color(0xFF64748B)          // Muted Slate
        )
    }

    val defaultColor = Color(0xFF94A3B8)

    if (totalExpense == 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info icon",
                    tint = Slate400,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No expenses logged to review category breakdown.",
                    color = Slate400,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left canvas represent donut
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val strokeWidth = 14.dp.toPx()

                    for ((category, amount) in sortedCategories) {
                        val pct = (amount / totalExpense).toFloat()
                        val sweep = pct * 360f
                        val col = categoryColors[category] ?: defaultColor

                        drawArc(
                            color = col,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Outflows", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatAmount(totalExpense),
                        color = Slate50,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right category listing with stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((category, amount) in sortedCategories.take(5)) {
                    val col = categoryColors[category] ?: defaultColor
                    val pct = (amount / totalExpense) * 100f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(col, shape = RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category,
                                color = Slate50,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.0f%% (%s)", pct, formatAmount(amount)),
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }
                if (sortedCategories.size > 5) {
                    Text(
                        text = "+ ${sortedCategories.size - 5} additional categories",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}


// --- TAB 2: TRANSACTIONS TIMELINE TIMELINE ---
@Composable
fun TransactionsTab(
    transactions: List<Transaction>,
    onEdit: (Transaction) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) } // null = All
    var categoryFilter by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Salary", "Freelance", "Food & Dining", "Transport", "Shopping", "Utilities", "Entertainment", "Savings Transfer", "Others")
    }

    // Filter computation
    val filteredTx = remember(transactions, searchQuery, typeFilter, categoryFilter) {
        transactions.filter { tx ->
            val matchSearch = tx.description.contains(searchQuery, ignoreCase = true) || 
                              tx.category.contains(searchQuery, ignoreCase = true) ||
                              tx.note.contains(searchQuery, ignoreCase = true)
            val matchType = typeFilter == null || tx.type == typeFilter
            val matchCategory = categoryFilter == "All" || tx.category == categoryFilter

            matchSearch && matchType && matchCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Search text entry
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by desc, category, note...", color = Slate400) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Slate400) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50,
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = Slate800,
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp)
        )

        // Type filter pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(null, TransactionType.INCOME, TransactionType.EXPENSE).forEach { type ->
                val label = when (type) {
                    null -> "All Actions"
                    TransactionType.INCOME -> "Incomes"
                    TransactionType.EXPENSE -> "Outflows"
                }
                val isSelected = typeFilter == type
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) EmeraldPrimary else Slate800,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { typeFilter = type }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Slate900 else Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Horizontal scrolling category filter
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = categoryFilter == cat
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Slate700 else Slate800.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) EmeraldPrimary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { categoryFilter = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Slate50 else Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        HorizontalDivider(color = Slate800, thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))

        // Infinite timeline scroll listing
        if (filteredTx.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching transactions found.\nAdjust filters or add a new record.",
                    color = Slate400,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTx) { tx ->
                    TransactionRow(
                        transaction = tx,
                        onEditClick = { onEdit(tx) },
                        onDeleteClick = { onDelete(tx.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Block
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (transaction.type == TransactionType.INCOME) EmeraldMuted else RoseMuted,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (transaction.category) {
                    "Salary" -> Icons.Default.Add
                    "Freelance" -> Icons.Default.Star
                    "Food & Dining" -> Icons.Filled.Add // Standard core shapes
                    "Transport" -> Icons.Default.Info
                    "Shopping" -> Icons.Default.PlayArrow
                    "Utilities" -> Icons.Default.Settings
                    "Entertainment" -> Icons.Default.Home
                    "Savings Transfer" -> Icons.Default.Star
                    else -> Icons.Default.Info
                }
                Icon(
                    imageVector = icon,
                    contentDescription = transaction.category,
                    tint = if (transaction.type == TransactionType.INCOME) EmeraldPrimary else RoseExpense,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    color = Slate50,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•",
                        color = Slate700,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = remember(transaction.date) { formatDate(transaction.date) },
                        color = Slate400,
                        fontSize = 11.sp
                    )
                }
                if (transaction.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: ${transaction.note}",
                        color = Slate400.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Value & Operations Actions Column
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (transaction.type == TransactionType.INCOME) "+${formatAmount(transaction.amount)}" 
                           else "-${formatAmount(transaction.amount)}",
                    color = if (transaction.type == TransactionType.INCOME) EmeraldPrimary else Slate50,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Action",
                        tint = Slate400,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEditClick() }
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Action",
                        tint = RoseExpense.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDeleteClick() }
                    )
                }
            }
        }
    }
}


// --- TAB 3: BUDGET PLANNING & SAVINGS VAULTS ---
@Composable
fun BudgetsAndGoalsTab(
    viewModel: FinanceViewModel
) {
    var showBudgetLimitDialog by remember { mutableStateOf(false) }
    var selectedBudgetCategory by remember { mutableStateOf("Food & Dining") }
    var budgetValueToSet by remember { mutableStateOf("") }

    var showSavingsGoalDialog by remember { mutableStateOf(false) }
    var newGoalName by remember { mutableStateOf("") }
    var newGoalTarget by remember { mutableStateOf("") }
    var newGoalInitial by remember { mutableStateOf("") }

    var contributeToGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var contributionVal by remember { mutableStateOf("") }

    val categoriesList = remember {
        listOf("Food & Dining", "Transport", "Shopping", "Utilities", "Entertainment", "Others")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PART A: Budget Limit setup panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BUDGET CATEGORIES CONTROL",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Button(
                onClick = { showBudgetLimitDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Configure Limit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Budget summary lists
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (limit in viewModel.budgetLimits) {
                val spent = viewModel.transactions
                    .filter { it.type == TransactionType.EXPENSE && it.category == limit.category }
                    .sumOf { it.amount }
                val pct = if (limit.amount > 0) spent / limit.amount else 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(limit.category, color = Slate50, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Spent: ${formatAmount(spent)} of ${formatAmount(limit.amount)} Limit",
                                color = if (spent > limit.amount) RoseExpense else Slate400,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (spent > limit.amount) RoseMuted else EmeraldMuted,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                String.format(Locale.US, "%.0f%%", pct * 100),
                                color = if (spent > limit.amount) RoseExpense else EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Slate800, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        // PART B: Savings Vault goals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVINGS VAULT GOALS",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Button(
                onClick = { showSavingsGoalDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Slate900),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("+ New Goals", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (viewModel.savingsGoals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate800, shape = RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No savings goals defined.\nGenerate one to lock away spare balance!",
                    color = Slate400,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (goal in viewModel.savingsGoals) {
                    val progress = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat() else 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Star Vault icon",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = goal.name,
                                        color = Slate50,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Goal",
                                    tint = RoseExpense,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.deleteSavingsGoal(goal.id) }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "${formatAmount(goal.currentAmount)} / ${formatAmount(goal.targetAmount)}",
                                    color = Slate50,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.US, "%.0f%% Done", progress * 100),
                                    color = EmeraldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress.coerceAtMost(1f),
                                color = EmeraldPrimary,
                                trackColor = Slate900,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { contributeToGoal = goal },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldMuted, contentColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Add Funds", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }

    // Modal dialog overlays
    if (showBudgetLimitDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetLimitDialog = false },
            containerColor = Slate800,
            title = { Text("Configure Budget Limit", color = Slate50) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Target Category", color = Slate400, fontSize = 12.sp)
                    // Dropdown simulation pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.take(3).forEach { cat ->
                            val isSelected = selectedBudgetCategory == cat
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) EmeraldPrimary else Slate900,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedBudgetCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(cat, color = if (isSelected) Slate900 else Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoriesList.drop(3).forEach { cat ->
                            val isSelected = selectedBudgetCategory == cat
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) EmeraldPrimary else Slate900,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedBudgetCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(cat, color = if (isSelected) Slate900 else Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = budgetValueToSet,
                        onValueChange = { budgetValueToSet = it },
                        label = { Text("Budget Limit ($)", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Slate900
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amt = budgetValueToSet.toDoubleOrNull() ?: 0.0
                        viewModel.updateBudgetLimit(selectedBudgetCategory, amt)
                        showBudgetLimitDialog = false
                        budgetValueToSet = ""
                    }
                ) {
                    Text("Save", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetLimitDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }

    if (showSavingsGoalDialog) {
        AlertDialog(
            onDismissRequest = { showSavingsGoalDialog = false },
            containerColor = Slate800,
            title = { Text("Establish Savings Vault", color = Slate50) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newGoalName,
                        onValueChange = { newGoalName = it },
                        label = { Text("Goal Name (e.g. Car, Emergency)", color = Slate400) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Slate900
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newGoalTarget,
                        onValueChange = { newGoalTarget = it },
                        label = { Text("Target Goal Amount ($)", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Slate900
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newGoalInitial,
                        onValueChange = { newGoalInitial = it },
                        label = { Text("Initial Saved Amount ($)", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Slate900
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = newGoalTarget.toDoubleOrNull() ?: 0.0
                        val initial = newGoalInitial.toDoubleOrNull() ?: 0.0
                        if (newGoalName.isNotBlank() && target > 0.0) {
                            viewModel.addSavingsGoal(newGoalName, target, initial)
                        }
                        showSavingsGoalDialog = false
                        newGoalName = ""
                        newGoalTarget = ""
                        newGoalInitial = ""
                    }
                ) {
                    Text("Create", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavingsGoalDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }

    if (contributeToGoal != null) {
        val goal = contributeToGoal!!
        AlertDialog(
            onDismissRequest = { contributeToGoal = null },
            containerColor = Slate800,
            title = { Text("Fund: ${goal.name}", color = Slate50) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "How much would you like to transfer from your liquidity balance into this goal partition?",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = contributionVal,
                        onValueChange = { contributionVal = it },
                        label = { Text("Contribution Amount ($)", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = Slate900
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = contributionVal.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            viewModel.contributeToSavingsGoal(goal.id, amount)
                        }
                        contributeToGoal = null
                        contributionVal = ""
                    }
                ) {
                    Text("Transfer", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contributeToGoal = null }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}


// --- TRANSACTIONS ENTRY & EDITING POPUP SHEET ---
@Composable
fun TransactionEntryDialog(
    transaction: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    val isEditing = transaction != null

    var amount by remember { mutableStateOf(transaction?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(transaction?.description ?: "") }
    var type by remember { mutableStateOf(transaction?.type ?: TransactionType.EXPENSE) }
    var category by remember { mutableStateOf(transaction?.category ?: "Food & Dining") }
    var note by remember { mutableStateOf(transaction?.note ?: "") }

    val categories = remember(type) {
        if (type == TransactionType.INCOME) {
            listOf("Salary", "Freelance", "Others")
        } else {
            listOf("Food & Dining", "Transport", "Shopping", "Utilities", "Entertainment", "Savings Transfer", "Others")
        }
    }

    // Match categories appropriately if type changed
    LaunchedEffect(type) {
        if (type == TransactionType.INCOME && category !in listOf("Salary", "Freelance", "Others")) {
            category = "Salary"
        } else if (type == TransactionType.EXPENSE && category in listOf("Salary", "Freelance")) {
            category = "Food & Dining"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isEditing) "Edit Transaction" else "Add Transaction",
                    color = Slate50,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Type Toggles Segmented pills color-matched
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate900, shape = RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == TransactionType.EXPENSE) RoseExpense else Color.Transparent)
                            .clickable { type = TransactionType.EXPENSE }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Outflow",
                            color = if (type == TransactionType.EXPENSE) Slate900 else Slate400,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (type == TransactionType.INCOME) EmeraldPrimary else Color.Transparent)
                            .clickable { type = TransactionType.INCOME }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Income",
                            color = if (type == TransactionType.INCOME) Slate900 else Slate400,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Amount Text Field Entry
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Amount ($)", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Slate900,
                        focusedContainerColor = Slate900,
                        unfocusedContainerColor = Slate900
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description Title
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Slate900,
                        focusedContainerColor = Slate900,
                        unfocusedContainerColor = Slate900
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector choices
                Text("Category", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EmeraldPrimary else Slate900)
                                .clickable { category = cat }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Slate900 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Optional note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional Notes", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = Slate900,
                        focusedContainerColor = Slate900,
                        unfocusedContainerColor = Slate900
                    ),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Slate400),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val doubleAmt = amount.toDoubleOrNull() ?: 0.0
                            if (doubleAmt > 0.0 && description.isNotBlank()) {
                                onSave(
                                    Transaction(
                                        id = transaction?.id ?: UUID.randomUUID().toString(),
                                        amount = doubleAmt,
                                        type = type,
                                        category = category,
                                        description = description,
                                        date = transaction?.date ?: System.currentTimeMillis(),
                                        note = note
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Slate900),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Global utility helper functions
fun formatAmount(amount: Double): String {
    return String.format(Locale.US, "$%,.2f", amount)
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}
