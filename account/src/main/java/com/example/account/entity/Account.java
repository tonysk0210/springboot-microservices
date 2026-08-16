package com.example.account.entity;

import jakarta.persistence.*;
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

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Integer customerId;

    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 100)
    private String accountType;

    @Column(name = "BRANCH_ADDRESS", nullable = false, length = 200)
    private String branchAddress;

    @Column(name = "COMMUNICATION_SW")
    private Boolean communicationSw;
}
