package com.example.account.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ACCOUNTS")
public class Account extends BaseEntity {

    @Id
    @Column(name = "ACCOUNT_NUMBER", nullable = false)
    private Integer accountNumber;

    @NotNull
    @Column(name = "CUSTOMER_ID", nullable = false)
    private Integer customerId;

    @Size(max = 100)
    @NotNull
    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 100)
    private String accountType;

    @Size(max = 200)
    @NotNull
    @Column(name = "BRANCH_ADDRESS", nullable = false, length = 200)
    private String branchAddress;


}