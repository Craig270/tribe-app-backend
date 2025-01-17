package com.savvato.tribeapp.controllers;

import com.savvato.tribeapp.controllers.annotations.controllers.ReviewDecisionAPIController.SaveReviewDecision;
import com.savvato.tribeapp.controllers.dto.ReviewDecisionRequest;
import com.savvato.tribeapp.dto.ReviewDecisionDTO;
import com.savvato.tribeapp.entities.ReviewDecision;
import com.savvato.tribeapp.services.ReviewDecisionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "reviewer-decision", description = "Decisions made after reviewing an attribute")
@RequestMapping("/api/reviewer-decision")
public class ReviewDecisionAPIController {
  @Autowired ReviewDecisionService reviewDecisionService;

  @SaveReviewDecision
  @PostMapping
  public ResponseEntity<ReviewDecisionDTO> saveReviewDecision(
      @RequestBody @Valid ReviewDecisionRequest request) {
    ReviewDecision decisionSaved =
        reviewDecisionService.saveReviewDecision(
            request.reviewId, request.reviewerId, request.reasonId);
    ReviewDecisionDTO rtn = ReviewDecisionDTO.builder().build();

    rtn.reviewId = decisionSaved.getReviewId();
    rtn.userId = decisionSaved.getUserId();
    rtn.reasonId = decisionSaved.getReasonId();
    return ResponseEntity.status(HttpStatus.OK).body(rtn);
  }
}
