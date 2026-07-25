package com.example.card.controller;

import com.example.card.dto.CardDto;
import com.example.card.dto.ErrorResponseDto;
import com.example.card.dto.ResponseDto;
import com.example.card.service.ICardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "卡片 CRUD API",                    // Swagger UI 的分組標題，取代預設的 card-controller
        description = "卡片資料的建立、查詢、更新、刪除"   // 分組標題下方的說明
)
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api", produces = {MediaType.APPLICATION_JSON_VALUE})
public class CardController {

    private final ICardService cardService;

    @Operation(summary = "建立卡片", description = "以手機號碼建立一張卡片，卡號自動產生")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "卡片建立成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確，或此號碼已有卡片紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/create-card")
    public ResponseEntity<ResponseDto> createCard(@RequestParam
                                                  @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                  String mobileNumber) {
        cardService.createCard(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(
                        HttpStatus.CREATED.toString(),
                        "卡片建立成功"));
    }

    @Operation(summary = "查詢卡片", description = "以手機號碼查詢卡片資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功",
                    content = @Content(schema = @Schema(implementation = CardDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的卡片紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/fetch-card")
    public ResponseEntity<CardDto> fetchCardDetails(@RequestParam
                                                    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                    String mobileNumber) {
        CardDto cardDto = cardService.fetchCard(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(cardDto);
    }

    @Operation(summary = "更新卡片", description = "以卡號為鍵，更新卡片資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "卡片更新成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "417", description = "卡片更新失敗",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此卡號的紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/update-card")
    public ResponseEntity<ResponseDto> updateCardDetails(@Valid @RequestBody CardDto cardDto) {
        boolean isUpdated = cardService.updateCard(cardDto);
        if (isUpdated) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "卡片更新成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "卡片更新失敗"));
        }
    }

    @Operation(summary = "刪除卡片", description = "以手機號碼刪除卡片資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "卡片刪除成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的卡片紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/delete-card")
    public ResponseEntity<ResponseDto> deleteCardDetails(@RequestParam
                                                         @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                         String mobileNumber) {
        boolean isDeleted = cardService.deleteCard(mobileNumber);
        if (isDeleted) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "卡片刪除成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "卡片刪除失敗"));
        }
    }
}
