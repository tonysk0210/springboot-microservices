package com.example.card.service;

import com.example.card.dto.CardDto;
import com.example.card.entity.Card;
import com.example.card.exception.CardAlreadyExistsException;
import com.example.card.exception.ResourceNotFoundException;
import com.example.card.mapper.CardsMapper;
import com.example.card.repository.CardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class CardServiceImpl implements ICardService {

    final private CardRepo cardRepo;

    @Override
    public void createCard(String mobileNumber) {
        // 1. 根據手機號碼查找卡片紀錄
        Optional<Card> optionalCards = cardRepo.findByMobileNumber(mobileNumber);
        if (optionalCards.isPresent()) {
            throw new CardAlreadyExistsException("此手機號碼已有卡片紀錄：" + mobileNumber);
        }
        // 2. 創建新的卡片紀錄
        cardRepo.save(createNewCard(mobileNumber));
    }

    @Override
    public CardDto fetchCard(String mobileNumber) {
        // 1. 根據手機號碼查找卡片紀錄
        Card card = cardRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Card", "mobileNumber", mobileNumber));
        // 2. 返回卡片資訊
        return CardsMapper.mapToCardDto(card, new CardDto());
    }

    @Override
    public boolean updateCard(CardDto cardDto) {
        // 1. 根據卡號查找卡片紀錄
        Card card = cardRepo.findByCardNumber(cardDto.getCardNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Card", "CardNumber", cardDto.getCardNumber()));
        CardsMapper.mapToCard(cardDto, card);

        // 2. 更新卡片資訊
        cardRepo.save(card);
        return true;
    }

    @Override
    public boolean deleteCard(String mobileNumber) {
        // 1. 根據手機號碼查找卡片紀錄
        Card card = cardRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Card", "mobileNumber", mobileNumber));
        // 2. 刪除卡片紀錄
        cardRepo.deleteById(card.getCardId());
        return true;
    }

    // ///////////////
    // helper method
    // ///////////////
    private Card createNewCard(String mobileNumber) {
        Card newCard = new Card();
        int randomCardNumber = 1_000_000_000 + new Random().nextInt(900_000_000);
        newCard.setCardNumber(Integer.toString(randomCardNumber));
        newCard.setMobileNumber(mobileNumber);
        newCard.setCardType("Credit Card");
        newCard.setTotalLimit(100_000);
        newCard.setAmountUsed(0);
        newCard.setAvailableAmount(100_000);
        return newCard;
    }
}
