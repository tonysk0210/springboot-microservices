package com.example.loan.service;


import com.example.loan.dto.LoanDto;
import com.example.loan.entity.Loan;
import com.example.loan.exception.LoanAlreadyExistsException;
import com.example.loan.exception.ResourceNotFoundException;
import com.example.loan.mapper.LoanMapper;
import com.example.loan.repository.LoanRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
@AllArgsConstructor
public class LoanServiceImpl implements ILoanService {

    private LoanRepo loanRepo;

    @Override
    public void createLoan(String mobileNumber) {
        // 1. 根據手機號碼查找貸款紀錄
        Optional<Loan> optionalLoan = loanRepo.findByMobileNumber(mobileNumber);
        // 2. 如果有貸款紀錄，則拋出異常
        if (optionalLoan.isPresent()) {
            throw new LoanAlreadyExistsException("此手機號碼已有貸款紀錄：" + mobileNumber);
        }
        // 3. 創建新的貸款紀錄
        loanRepo.save(createNewLoan(mobileNumber));
    }

    @Override
    public LoanDto fetchLoan(String mobileNumber) {
        // 1. 根據手機號碼查找貸款紀錄
        Loan loan = loanRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Loan", "mobileNumber", mobileNumber));
        // 2. 將貸款紀錄轉換為貸款 DTO
        return LoanMapper.mapToLoanDto(loan, new LoanDto());
    }


    @Override
    public boolean updateLoan(LoanDto loanDto) {
        // 1. 根據貸款編號查找貸款紀錄
        Loan loan = loanRepo.findByLoanNumber(loanDto.getLoanNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Loan", "LoanNumber", loanDto.getLoanNumber()));
        // 2. 將貸款紀錄轉換為貸款 DTO
        LoanMapper.mapToLoan(loanDto, loan);
        // 3. 儲存更新後的貸款紀錄
        loanRepo.save(loan);
        return true;
    }


    @Override
    public boolean deleteLoan(String mobileNumber) {
        // 1. 根據手機號碼查找貸款紀錄
        Loan loan = loanRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Loan", "mobileNumber", mobileNumber)
                );
        // 2. 刪除貸款紀錄
        loanRepo.deleteById(loan.getLoanId());
        return true;
    }

    // ///////////////
    // helper method
    // ///////////////
    private Loan createNewLoan(String mobileNumber) {
        Loan newLoan = new Loan();
        int randomLoanNumber = 1_000_000_000 + new Random().nextInt(900_000_000);
        newLoan.setLoanNumber(Integer.toString(randomLoanNumber));
        newLoan.setMobileNumber(mobileNumber);
        newLoan.setLoanType("Home Loan");
        newLoan.setTotalLoan(100_000);
        newLoan.setAmountPaid(0);
        newLoan.setOutstandingAmount(100_000);
        return newLoan;
    }
}
