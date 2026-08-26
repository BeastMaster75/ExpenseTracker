package com.expensetracker.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @JsonIgnore
    @Column(length = 512)
    private String passwordHistory;

    @Column()
    private Date changeCredential;

    private BigDecimal balance = BigDecimal.ZERO;

    private BigDecimal initialBalance = BigDecimal.ZERO;

    private BigDecimal totalIncome = BigDecimal.ZERO;

    private BigDecimal totalExpense = BigDecimal.ZERO;

    @Column()
    private Boolean isDeleted = false;

    @Column()
    private Boolean isConfirmed = false;

    @JsonIgnore
    @Column(length = 512)
    private String emailVerificationTokenHash;

    @JsonIgnore
    @Column()
    @Temporal(TemporalType.TIMESTAMP)
    private Date emailVerificationTokenExpiresAt;
}