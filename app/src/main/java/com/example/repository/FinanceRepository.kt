package com.example.repository

import android.content.Context
import android.util.Log
import com.example.model.BudgetLimit
import com.example.model.SavingsGoal
import com.example.model.Transaction
import com.example.model.TransactionType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Calendar

class FinanceRepository(private val context: Context) {

    private val sharedPreferences = context.getSharedPreferences("finance_tracker_prefs", Context.MODE_PRIVATE)

    private val moshi: Moshi = try {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    } catch (e: Exception) {
        Log.e("FinanceRepository", "Failed to initialize Moshi, will rely on backup parser", e)
        Moshi.Builder().build()
    }

    // Keys for standard storage
    private val KEY_TRANSACTIONS = "key_transactions"
    private val KEY_BUDGETS = "key_budgets"
    private val KEY_SAVINGS = "key_savings"

    fun getTransactions(): List<Transaction> {
        val json = sharedPreferences.getString(KEY_TRANSACTIONS, null)
        if (json.isNullOrBlank()) {
            val initial = createInitialTransactions()
            saveTransactions(initial)
            return initial
        }
        return try {
            val type = Types.newParameterizedType(List::class.java, Transaction::class.java)
            val adapter = moshi.adapter<List<Transaction>>(type)
            adapter.fromJson(json) ?: createInitialTransactions()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi parsing failed for transactions, fallback to custom list", e)
            try {
                // Foolproof manual parser if reflection adapter hits compile issues or proguard blocks
                parseTransactionsFallback(json)
            } catch (e2: Exception) {
                createInitialTransactions()
            }
        }
    }

    fun saveTransactions(transactions: List<Transaction>) {
        try {
            val type = Types.newParameterizedType(List::class.java, Transaction::class.java)
            val adapter = moshi.adapter<List<Transaction>>(type)
            val json = adapter.toJson(transactions)
            sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi serialization failed for transactions, fallback to manual", e)
            val manualStr = serializeTransactionsFallback(transactions)
            sharedPreferences.edit().putString(KEY_TRANSACTIONS, manualStr).apply()
        }
    }

    fun getBudgetLimits(): List<BudgetLimit> {
        val json = sharedPreferences.getString(KEY_BUDGETS, null)
        if (json.isNullOrBlank()) {
            val initial = createInitialBudgets()
            saveBudgetLimits(initial)
            return initial
        }
        return try {
            val type = Types.newParameterizedType(List::class.java, BudgetLimit::class.java)
            val adapter = moshi.adapter<List<BudgetLimit>>(type)
            adapter.fromJson(json) ?: createInitialBudgets()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi parsing failed for budgets", e)
            createInitialBudgets()
        }
    }

    fun saveBudgetLimits(budgets: List<BudgetLimit>) {
        try {
            val type = Types.newParameterizedType(List::class.java, BudgetLimit::class.java)
            val adapter = moshi.adapter<List<BudgetLimit>>(type)
            val json = adapter.toJson(budgets)
            sharedPreferences.edit().putString(KEY_BUDGETS, json).apply()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi serialization failed for budgets", e)
        }
    }

    fun getSavingsGoals(): List<SavingsGoal> {
        val json = sharedPreferences.getString(KEY_SAVINGS, null)
        if (json.isNullOrBlank()) {
            val initial = createInitialSavingsGoals()
            saveSavingsGoals(initial)
            return initial
        }
        return try {
            val type = Types.newParameterizedType(List::class.java, SavingsGoal::class.java)
            val adapter = moshi.adapter<List<SavingsGoal>>(type)
            adapter.fromJson(json) ?: createInitialSavingsGoals()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi parsing failed for savings goals", e)
            createInitialSavingsGoals()
        }
    }

    fun saveSavingsGoals(goals: List<SavingsGoal>) {
        try {
            val type = Types.newParameterizedType(List::class.java, SavingsGoal::class.java)
            val adapter = moshi.adapter<List<SavingsGoal>>(type)
            val json = adapter.toJson(goals)
            sharedPreferences.edit().putString(KEY_SAVINGS, json).apply()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Moshi serialization failed for savings goals", e)
        }
    }

    private fun createInitialTransactions(): List<Transaction> {
        val cal = Calendar.getInstance()
        
        cal.add(Calendar.DAY_OF_YEAR, -5)
        val t5DaysAgo = cal.timeInMillis

        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -3)
        val t3DaysAgo = cal.timeInMillis

        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val t1DayAgo = cal.timeInMillis

        val tNow = System.currentTimeMillis()

        return listOf(
            Transaction(
                amount = 4500.00,
                type = TransactionType.INCOME,
                category = "Salary",
                description = "Monthly Tech Corp Salary",
                date = t5DaysAgo,
                note = "Base package + transport allowance"
            ),
            Transaction(
                amount = 120.50,
                type = TransactionType.EXPENSE,
                category = "Food & Dining",
                description = "Supermarket Weekly Buy",
                date = t3DaysAgo,
                note = "Organic groceries and snacks"
            ),
            Transaction(
                amount = 45.00,
                type = TransactionType.EXPENSE,
                category = "Transport",
                description = "Gas refill",
                date = t3DaysAgo
            ),
            Transaction(
                amount = 89.99,
                type = TransactionType.EXPENSE,
                category = "Utilities",
                description = "High-speed Internet & TV",
                date = t3DaysAgo,
                note = "Auto-pay"
            ),
            Transaction(
                amount = 750.00,
                type = TransactionType.INCOME,
                category = "Freelance",
                description = "Web App Design Mockups",
                date = t1DayAgo,
                note = "Client: Design Agency X"
            ),
            Transaction(
                amount = 35.50,
                type = TransactionType.EXPENSE,
                category = "Food & Dining",
                description = "Dinner at Ramen Bar",
                date = t1DayAgo
            ),
            Transaction(
                amount = 15.00,
                type = TransactionType.EXPENSE,
                category = "Transport",
                description = "Uber to downtown",
                date = tNow
            ),
            Transaction(
                amount = 199.99,
                type = TransactionType.EXPENSE,
                category = "Shopping",
                description = "Mechanical Keyboard",
                date = tNow,
                note = "Cherry MX Brown switches"
            ),
            Transaction(
                amount = 25.00,
                type = TransactionType.EXPENSE,
                category = "Entertainment",
                description = "Cinema Tickets & Popcorn",
                date = tNow
            )
        )
    }

    private fun createInitialBudgets(): List<BudgetLimit> {
        return listOf(
            BudgetLimit("Food & Dining", 600.00),
            BudgetLimit("Transport", 200.00),
            BudgetLimit("Shopping", 400.00),
            BudgetLimit("Utilities", 150.00),
            BudgetLimit("Entertainment", 150.00)
        )
    }

    private fun createInitialSavingsGoals(): List<SavingsGoal> {
        return listOf(
            SavingsGoal(name = "Emergency Fund", targetAmount = 10000.00, currentAmount = 5200.00),
            SavingsGoal(name = "New Pro Laptop", targetAmount = 2400.00, currentAmount = 1200.00),
            SavingsGoal(name = "Japan Trip 2027", targetAmount = 4000.00, currentAmount = 1500.00)
        )
    }

    // Simple manual parser fallbacks for safety if reflection ever has library loader problems in runtime.
    private fun parseTransactionsFallback(json: String): List<Transaction> {
        val list = mutableListOf<Transaction>()
        try {
            // Very simplified parsing of manual serialization format, e.g., standard CSV style separated by "###"
            val parts = json.split("###")
            for (p in parts) {
                if (p.isBlank()) continue
                val fields = p.split("|||")
                if (fields.size >= 6) {
                    val tx = Transaction(
                        id = fields[0],
                        amount = fields[1].toDouble(),
                        type = TransactionType.valueOf(fields[2]),
                        category = fields[3],
                        description = fields[4],
                        date = fields[5].toLong(),
                        note = if (fields.size > 6) fields[6] else ""
                    )
                    list.add(tx)
                }
            }
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Fallback parser failed too", e)
        }
        return if (list.isEmpty()) createInitialTransactions() else list
    }

    private fun serializeTransactionsFallback(list: List<Transaction>): String {
        val sb = StringBuilder()
        for (tx in list) {
            sb.append(tx.id).append("|||")
                .append(tx.amount).append("|||")
                .append(tx.type.name).append("|||")
                .append(tx.category).append("|||")
                .append(tx.description).append("|||")
                .append(tx.date).append("|||")
                .append(tx.note)
                .append("###")
        }
        return sb.toString()
    }
}
