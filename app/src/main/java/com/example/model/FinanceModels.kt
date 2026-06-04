package com.example.model

import java.util.UUID

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val note: String = ""
)

enum class TransactionType {
    INCOME,
    EXPENSE
}

data class BudgetLimit(
    val category: String,
    val amount: Double
)

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double
)
