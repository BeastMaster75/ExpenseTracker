package com.expensetracker.transaction.entity;

import com.expensetracker.budget.entity.Budget;
import com.expensetracker.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id")
    private Budget budget;

    private String type;

    private BigDecimal amount;

    private String description;

    private Date occurredAt;

    private Date createdAt;

    private Date updatedAt;

    @Column()
    private Boolean isDeleted = false;
}
