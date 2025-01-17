package com.savvato.tribeapp.controllers;

import com.savvato.tribeapp.controllers.annotations.controllers.NotificationController.GetUserNotifications;
import com.savvato.tribeapp.controllers.annotations.controllers.NotificationController.UpdateNotification;
import com.savvato.tribeapp.controllers.dto.NotificationRequest;
import com.savvato.tribeapp.dto.NotificationDTO;
import com.savvato.tribeapp.dto.GenericResponseDTO;
import com.savvato.tribeapp.services.GenericResponseService;
import com.savvato.tribeapp.services.NotificationService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@Tag(
    name = "notifications",
    description =
        "Notifications about status of user actions (e.g. approval of submitted attribute)")
public class NotificationAPIController {

  @Autowired private NotificationService notificationService;
  @Autowired private GenericResponseService GenericResponseService;

  @UpdateNotification
  @PutMapping
  public ResponseEntity<GenericResponseDTO> updateNotification(@RequestBody @Valid NotificationRequest req) {
      boolean isRead = notificationService.checkNotificationReadStatus(req.id);
      if (isRead) {
          GenericResponseDTO rtn = GenericResponseService.createDTO("Notification is already read");
          return ResponseEntity.ok(rtn);
      } else {
          notificationService.updateNotificationReadStatus(req.id);
          GenericResponseDTO rtn = GenericResponseService.createDTO("Notification read status updated");

          return ResponseEntity.ok(rtn);
      }
  }

    @DeleteMapping("/{id}")
    public ResponseEntity<GenericResponseDTO> deleteNotification(@PathVariable Long id) {
        boolean exists = notificationService.checkNotificationExists(id);
        if (exists) {
            notificationService.deleteNotification(id);
            GenericResponseDTO rtn =  GenericResponseService.createDTO("Notification deleted");
            return ResponseEntity.ok(rtn);
        } else {
            GenericResponseDTO rtn = GenericResponseService.createDTO("Bad Request");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(rtn);
        }

    }

  @GetUserNotifications
  @GetMapping("/user/{user_id}")
  public ResponseEntity<List<NotificationDTO>> getUserNotifications(
      @Parameter(description = "A user ID", example = "1") @PathVariable Long user_id) {
    List<NotificationDTO> rtn = notificationService.getUserNotifications(user_id);
    return ResponseEntity.ok(rtn);
  };
}
