package com.expensetracker.user.entity;

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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column()
    private Date changeCredential;

    private BigDecimal balance = BigDecimal.ZERO;

    private BigDecimal totalIncome = BigDecimal.ZERO;

    private BigDecimal totalExpense = BigDecimal.ZERO;

    @Column()
    private Boolean isDeleted = false;

    @Column()
    private Boolean isConfirmed = false;
}
