package com.example.card.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "CARDS")
public class Card extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CARD_ID", nullable = false)
    private Integer cardId;

    @Column(name = "MOBILE_NUMBER", nullable = false, length = 15)
    private String mobileNumber;

    @Column(name = "CARD_NUMBER", nullable = false, length = 100)
    private String cardNumber;

    @Column(name = "CARD_TYPE", nullable = false, length = 100)
    private String cardType;

    @Column(name = "TOTAL_LIMIT", nullable = false)
    private Integer totalLimit;

    @Column(name = "AMOUNT_USED", nullable = false)
    private Integer amountUsed;

    @Column(name = "AVAILABLE_AMOUNT", nullable = false)
    private Integer availableAmount;
}