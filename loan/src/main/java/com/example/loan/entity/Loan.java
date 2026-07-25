package com.example.loan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "LOANS")
public class Loan extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOAN_ID", nullable = false)
    private Integer loanId;

    @Column(name = "MOBILE_NUMBER", nullable = false, length = 15)
    private String mobileNumber;

    @Column(name = "LOAN_NUMBER", nullable = false, length = 100)
    private String loanNumber;

    @Column(name = "LOAN_TYPE", nullable = false, length = 100)
    private String loanType;

    @Column(name = "TOTAL_LOAN", nullable = false)
    private Integer totalLoan;

    @Column(name = "AMOUNT_PAID", nullable = false)
    private Integer amountPaid;

    @Column(name = "OUTSTANDING_AMOUNT", nullable = false)
    private Integer outstandingAmount;
}