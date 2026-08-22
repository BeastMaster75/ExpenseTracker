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

    @Column(nullable = false)
    private String password;

    // The last 5 BCrypt hashes this account has used, newest first, comma
    // separated. A hash is "$2a$<cost>$<53 chars of [./A-Za-z0-9]>", so a comma
    // can never occur inside one and is safe as a separator.
    // 5 x 60 chars + 4 separators = 304, so 512 leaves headroom.
    @JsonIgnore
    @Column(length = 512)
    private String passwordHistory;

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
