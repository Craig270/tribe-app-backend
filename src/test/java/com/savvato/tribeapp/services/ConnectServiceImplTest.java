package com.savvato.tribeapp.services;

import com.savvato.tribeapp.config.principal.UserPrincipal;
import com.savvato.tribeapp.controllers.dto.ConnectionRemovalRequest;
import com.savvato.tribeapp.dto.ConnectIncomingMessageDTO;
import com.savvato.tribeapp.dto.ConnectOutgoingMessageDTO;
import com.savvato.tribeapp.dto.GenericResponseDTO;
import com.savvato.tribeapp.entities.Connection;
import com.savvato.tribeapp.repositories.ConnectionsRepository;
import com.savvato.tribeapp.repositories.UserRepository;
import liquibase.pro.packaged.U;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class})
public class ConnectServiceImplTest extends AbstractServiceImplTest {
    @TestConfiguration
    static class ConnectServiceImplTestContextConfiguration {
        @Bean
        public ConnectService connectService() {
            return new ConnectServiceImpl();
        }

        @Bean
        public CacheService cacheService() {
            return new CacheServiceImpl();
        }
    }

    @Autowired
    ConnectService connectService;

    @MockBean
    CacheService cacheService;

    @MockBean
    ConnectionsRepository connectionsRepository;

    @MockBean
    SimpMessagingTemplate simpMessagingTemplate;

    @MockBean
    UserRepository userRepository;

    @MockBean
    UserService userService;

    @MockBean
    GenericResponseService genericResponseService;

    @Test
    public void getQRCodeString() {
        Long userId = USER1_ID;
        Optional<String> qrCodeString = Optional.of("QR code");
        Mockito.when(cacheService.get(Mockito.any(), Mockito.any())).thenReturn(qrCodeString.get());
        Optional<String> rtn = connectService.getQRCodeString(userId);
        assertEquals(qrCodeString, rtn);
    }

    @Test
    public void storeQRCodeString() {
        Long userId = USER1_ID;
        Optional<String> generatedQRCodeString = Optional.of("QR code");

        Optional<String> rtn = connectService.storeQRCodeString(userId);

        ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(1)).put(arg1.capture(), arg2.capture(), Mockito.any());

        assertEquals(arg1.getValue(), "ConnectQRCodeString");
        assertEquals(arg2.getValue(), String.valueOf(userId));
    }

    @Test
    public void saveConnectionDetailsHappyPath() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;

        Boolean connectionStatus = connectService.saveConnectionDetails(requestingUserId, toBeConnectedWithUserId);
        assertEquals(connectionStatus, true);
    }

    @Test
    public void saveConnectionDetailsUnhappyPath() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        doThrow(new IndexOutOfBoundsException()).when(connectionsRepository).save(Mockito.any());
        Boolean connectionStatus = connectService.saveConnectionDetails(requestingUserId, toBeConnectedWithUserId);
        assertEquals(connectionStatus, false);
    }

    @Test
    public void testValidateConnectionHappyPath() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;

        when(userService.getLoggedInUserId()).thenReturn(USER1_ID);
        when(connectionsRepository.findExistingConnectionWithReversedUserIds(anyLong(), anyLong())).thenReturn(Optional.empty());

        Optional<GenericResponseDTO> validateConnection = connectService.validateConnection(requestingUserId, toBeConnectedWithUserId);

        assertThat(validateConnection.isEmpty());
        verify(userService, times(1)).getLoggedInUserId();
        verify(connectionsRepository, times(1)).findExistingConnectionWithReversedUserIds(anyLong(), anyLong());
    }

    @Test
    public void testValidateConnectionWhenExistingConnectionWithReversedUserIdsExists() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        Connection existingConnection = new Connection(requestingUserId, toBeConnectedWithUserId);
        GenericResponseDTO expectedGenericResponseDTO = GenericResponseDTO.builder()
                .booleanMessage(false)
                .responseMessage("This connection already exists in reverse between the requesting user " + requestingUserId + " and the to be connected with user " + toBeConnectedWithUserId)
                .build();

        when(userService.getLoggedInUserId()).thenReturn(USER1_ID);
        when(connectionsRepository.findExistingConnectionWithReversedUserIds(anyLong(), anyLong())).thenReturn(Optional.of(existingConnection));

        Optional<GenericResponseDTO> validateConnection = connectService.validateConnection(requestingUserId, toBeConnectedWithUserId);

        assertThat(expectedGenericResponseDTO).usingRecursiveComparison().isEqualTo(validateConnection.get());
        verify(userService, times(1)).getLoggedInUserId();
        verify(connectionsRepository, times(1)).findExistingConnectionWithReversedUserIds(anyLong(), anyLong());
    }

    @Test
    public void testValidateConnectionWhenIdsAreTheSame() {
        Long requestingUserId = USER2_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        GenericResponseDTO expectedGenericResponseDTO = GenericResponseDTO.builder()
                .booleanMessage(false)
                .responseMessage("User " + requestingUserId + " may not connect with themselves")
                .build();

        when(userService.getLoggedInUserId()).thenReturn(USER2_ID);

        Optional<GenericResponseDTO> validateConnection = connectService.validateConnection(requestingUserId, toBeConnectedWithUserId);

        assertThat(expectedGenericResponseDTO).usingRecursiveComparison().isEqualTo(validateConnection.get());
        verify(userService, times(1)).getLoggedInUserId();
        verify(connectionsRepository, never()).findExistingConnectionWithReversedUserIds(anyLong(), anyLong());
    }

    @Test
    public void testValidateConnectionWhenRequestingUserNotLoggedIn() {
        Long loggedInUser = 3L;
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        GenericResponseDTO expectedGenericResponseDTO = GenericResponseDTO.builder()
                .booleanMessage(false)
                .responseMessage("The logged in user (" + loggedInUser + ") does not match issuing user (" + requestingUserId + ")")
                .build();

        when(userService.getLoggedInUserId()).thenReturn(loggedInUser);

        Optional<GenericResponseDTO> validateConnection = connectService.validateConnection(requestingUserId,toBeConnectedWithUserId);

        assertThat(expectedGenericResponseDTO).usingRecursiveComparison().isEqualTo(validateConnection.get());
        verify(userService, times(1)).getLoggedInUserId();
        verify(connectionsRepository, never()).findExistingConnectionWithReversedUserIds(anyLong(), anyLong());
    }

    @Test
    public void connectWhenQrCodeIsInvalid() {
        UserPrincipal user = new UserPrincipal(getUser1());
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String qrcodePhrase = "invalid code";
        String connectionIntent = "";
        String expectedDestination = "/connect/user/queue/specific-user";
        ConnectIncomingMessageDTO incoming = ConnectIncomingMessageDTO.builder()
                .requestingUserId(requestingUserId)
                .toBeConnectedWithUserId(toBeConnectedWithUserId)
                .qrcodePhrase(qrcodePhrase)
                .connectionIntent(connectionIntent)
                .build();
        ConnectOutgoingMessageDTO outgoing = ConnectOutgoingMessageDTO.builder()
                .connectionError(true)
                .message("Invalid QR code; failed to connect.")
                .build();
        ConnectService connectServiceSpy = spy(connectService);
        doReturn(false).when(connectServiceSpy).validateQRCode(Mockito.any(), Mockito.any());

        connectServiceSpy.connect(incoming);

        verify(connectServiceSpy, never()).handleConnectionIntent(Mockito.any(), Mockito.any(), Mockito.any());
        ArgumentCaptor<String> recipientArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ConnectOutgoingMessageDTO> outgoingMsgArg = ArgumentCaptor.forClass(ConnectOutgoingMessageDTO.class);

        verify(simpMessagingTemplate, times(1)).convertAndSendToUser(recipientArg.capture(), destinationArg.capture(), outgoingMsgArg.capture());
        assertEquals(recipientArg.getValue(), String.valueOf(toBeConnectedWithUserId));
        assertEquals(destinationArg.getValue(), expectedDestination);
        assertThat(outgoingMsgArg.getValue()).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void connectWhenQrCodeIsValid() {
        UserPrincipal user = new UserPrincipal(getUser1());
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String connectionIntent = "";
        String expectedDestination = "/connect/user/queue/specific-user";
        ConnectIncomingMessageDTO incoming = ConnectIncomingMessageDTO.builder()
                .requestingUserId(requestingUserId)
                .toBeConnectedWithUserId(toBeConnectedWithUserId)
                .connectionIntent(connectionIntent)
                .build();
        List<ConnectOutgoingMessageDTO> outgoing = new ArrayList<>();
        outgoing.add(ConnectOutgoingMessageDTO.builder()
                .message("Please confirm that you wish to connect.")
                .to(getUsernameDTOForUserID(toBeConnectedWithUserId))
                .build());
        ConnectService connectServiceSpy = spy(connectService);
        doReturn(true).when(connectServiceSpy).validateQRCode(Mockito.any(), Mockito.any());
        doReturn(outgoing).when(connectServiceSpy).handleConnectionIntent(Mockito.any(), Mockito.any(), Mockito.any());

        connectServiceSpy.connect(incoming);

        ArgumentCaptor<String> connectionIntentArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> requestingUserIdArg = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> toBeConnectedWithUserIdArg = ArgumentCaptor.forClass(Long.class);
        verify(connectServiceSpy, times(1)).handleConnectionIntent(connectionIntentArg.capture(), requestingUserIdArg.capture(), toBeConnectedWithUserIdArg.capture());
        assertEquals(connectionIntentArg.getValue(), connectionIntent);
        assertEquals(requestingUserIdArg.getValue(), requestingUserId);
        assertEquals(toBeConnectedWithUserIdArg.getValue(), toBeConnectedWithUserId);

        ArgumentCaptor<String> recipientArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ArrayList<ConnectOutgoingMessageDTO>> outgoingMsgArg = ArgumentCaptor.forClass(ArrayList.class);
        verify(simpMessagingTemplate, times(1)).convertAndSendToUser(recipientArg.capture(), destinationArg.capture(), outgoingMsgArg.capture());
        assertEquals(recipientArg.getValue(), String.valueOf(outgoing.get(0).to));
        assertEquals(destinationArg.getValue(), expectedDestination);
        assertThat(outgoingMsgArg.getValue()).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void handleConnectionIntentWhenNoConnectionIntent() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String connectionIntent = "";
        List<ConnectOutgoingMessageDTO> expectedOutgoingMsg = new ArrayList<>();
        expectedOutgoingMsg.add(ConnectOutgoingMessageDTO.builder()
                .message("Please confirm that you wish to connect.")
                .to(getUsernameDTOForUserID(toBeConnectedWithUserId))
                .build());
        ConnectService connectServiceSpy = spy(connectService);
        Mockito.when(userRepository.findById(Mockito.any())).thenReturn(Optional.of(getUser2()));
        List<ConnectOutgoingMessageDTO> outgoing = connectServiceSpy.handleConnectionIntent(connectionIntent, requestingUserId, toBeConnectedWithUserId);

        verify(connectServiceSpy, never()).saveConnectionDetails(Mockito.any(), Mockito.any());
        verify(connectionsRepository, never()).save(Mockito.any());
        assertThat(expectedOutgoingMsg).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void handleConnectionIntentWhenConnectionIntentConfirmedAndDatabaseSaveSuccessful() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String connectionIntent = "confirmed";
        ArrayList<Long> recipients = new ArrayList<>(Arrays.asList(requestingUserId, toBeConnectedWithUserId));
        List<ConnectOutgoingMessageDTO> expectedOutgoingMsg = new ArrayList<>();
        for(Long id : recipients) {
            expectedOutgoingMsg.add(ConnectOutgoingMessageDTO.builder()
                    .connectionSuccess(true)
                    .to(getUsernameDTOForUserID(id))
                    .message("Successfully saved connection!")
                    .build());
        }
        Connection connection = new Connection(requestingUserId, toBeConnectedWithUserId);
        Mockito.when(connectionsRepository.save(Mockito.any())).thenReturn(connection);
        Mockito.when(userRepository.findById(Mockito.any())).thenReturn(Optional.of(getUser1())).thenReturn(Optional.of(getUser2()));
        List<ConnectOutgoingMessageDTO> outgoing = connectService.handleConnectionIntent(connectionIntent, requestingUserId, toBeConnectedWithUserId);

        ArgumentCaptor<Connection> connectionArg = ArgumentCaptor.forClass(Connection.class);
        verify(connectionsRepository, times(1)).save(connectionArg.capture());
        assertEquals(connectionArg.getValue().getToBeConnectedWithUserId(), toBeConnectedWithUserId);
        assertEquals(connectionArg.getValue().getRequestingUserId(), requestingUserId);
        assertThat(expectedOutgoingMsg).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void handleConnectionIntentWhenConnectionIntentConfirmedAndDatabaseSaveUnsuccessful() throws Exception {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String connectionIntent = "confirmed";
        ArrayList<Long> recipients = new ArrayList<>(Arrays.asList(requestingUserId, toBeConnectedWithUserId));
        List<ConnectOutgoingMessageDTO> expectedOutgoingMsg = new ArrayList<>();
        for(Long id : recipients) {
            expectedOutgoingMsg.add(ConnectOutgoingMessageDTO.builder()
                    .connectionError(true)
                    .to(getUsernameDTOForUserID(id))
                    .message("Failed to save connection to database.")
                    .build());
        }

        Mockito.when(connectionsRepository.save(Mockito.any())).thenThrow(new NullPointerException("Something went wrong."));
        Mockito.when(userRepository.findById(Mockito.any())).thenReturn(Optional.of(getUser1())).thenReturn(Optional.of(getUser2()));
        List<ConnectOutgoingMessageDTO> outgoing = connectService.handleConnectionIntent(connectionIntent, requestingUserId, toBeConnectedWithUserId);

        ArgumentCaptor<Connection> connectionArg = ArgumentCaptor.forClass(Connection.class);
        verify(connectionsRepository, times(1)).save(connectionArg.capture());
        assertEquals(connectionArg.getValue().getToBeConnectedWithUserId(), toBeConnectedWithUserId);
        assertEquals(connectionArg.getValue().getRequestingUserId(), requestingUserId);
        assertThat(expectedOutgoingMsg).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void handleConnectionIntentWhenConnectionIntentDenied() {
        Long requestingUserId = USER1_ID;
        Long toBeConnectedWithUserId = USER2_ID;
        String connectionIntent = "denied";
        ArrayList<Long> recipients = new ArrayList<>(Arrays.asList(requestingUserId, toBeConnectedWithUserId));
        List<ConnectOutgoingMessageDTO> expectedOutgoingMsg = new ArrayList<>();
        for(Long id : recipients) {
            expectedOutgoingMsg.add(ConnectOutgoingMessageDTO.builder()
                    .connectionError(true)
                    .to(getUsernameDTOForUserID(id))
                    .message("Connection request denied.")
                    .build());
        }
        ConnectService connectServiceSpy = spy(connectService);
        Mockito.when(userRepository.findById(Mockito.any())).thenReturn(Optional.of(getUser1())).thenReturn(Optional.of(getUser2()));
        List<ConnectOutgoingMessageDTO> outgoing = connectServiceSpy.handleConnectionIntent(connectionIntent, requestingUserId, toBeConnectedWithUserId);

        verify(connectServiceSpy, never()).saveConnectionDetails(Mockito.any(), Mockito.any());
        verify(connectionsRepository, never()).save(Mockito.any());
        assertThat(expectedOutgoingMsg).usingRecursiveComparison().isEqualTo(outgoing);
    }

    @Test
    public void validateQRCode() {
        ConnectService connectServiceSpy = spy(connectService);
        Optional<String> qrCodeOpt = Optional.of("ABCDEFGHIJKL");
        String providedQRCode = "ABCDEFGHIJKL";
        Long toBeConnectedWithUserId = USER1_ID;
        doReturn(qrCodeOpt).when(connectServiceSpy).getQRCodeString(anyLong());
        Boolean isValid = connectServiceSpy.validateQRCode(providedQRCode, toBeConnectedWithUserId);
        assertTrue(isValid);
    }

    @Test
    public void removeConnectionHappyPath() {
        ConnectionRemovalRequest connectionDeleteRequest = new ConnectionRemovalRequest();
        connectionDeleteRequest.requestingUserId = USER1_ID;
        connectionDeleteRequest.connectedWithUserId = USER2_ID;
        ArgumentCaptor<Long> requestingUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> connectedWithUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        assertTrue(connectService.removeConnection(connectionDeleteRequest));
        verify(connectionsRepository, times(1)).removeConnection(requestingUserIdCaptor.capture(), connectedWithUserIdCaptor.capture());
        assertEquals(requestingUserIdCaptor.getValue(), connectionDeleteRequest.requestingUserId);
        assertEquals(connectedWithUserIdCaptor.getValue(), connectionDeleteRequest.connectedWithUserId);
    }

    @Test
    public void removeConnectionWhenDatabaseDeleteFails() {
        ConnectionRemovalRequest connectionDeleteRequest = new ConnectionRemovalRequest();
        connectionDeleteRequest.requestingUserId = USER1_ID;
        connectionDeleteRequest.connectedWithUserId = USER2_ID;
        ArgumentCaptor<Long> requestingUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> connectedWithUserIdCaptor = ArgumentCaptor.forClass(Long.class);
        doThrow(new IllegalArgumentException("Database delete failed.")).when(connectionsRepository).removeConnection(anyLong(), anyLong());
        assertFalse(connectService.removeConnection(connectionDeleteRequest));
        verify(connectionsRepository, times(1)).removeConnection(requestingUserIdCaptor.capture(), connectedWithUserIdCaptor.capture());
        assertEquals(requestingUserIdCaptor.getValue(), connectionDeleteRequest.requestingUserId);
        assertEquals(connectedWithUserIdCaptor.getValue(), connectionDeleteRequest.connectedWithUserId);
    }

    @Test
    public void removeConnectionWhenBothIdsAreTheSame() {
        ConnectionRemovalRequest connectionRemovalRequest = new ConnectionRemovalRequest();
        connectionRemovalRequest.requestingUserId = USER1_ID;
        connectionRemovalRequest.connectedWithUserId = USER1_ID;
        assertFalse(connectService.removeConnection(connectionRemovalRequest));
        verify(connectionsRepository, never()).removeConnection(anyLong(), anyLong());
    }

    @Test
    public void testGetAllConnectionsForAUserWhenConnectionsExist() {
        Long toBeConnectedUserId = USER2_ID;

        Connection connection = new Connection();
        connection.setCreated();
        connection.setRequestingUserId(USER1_ID);
        connection.setToBeConnectedWithUserId(toBeConnectedUserId);

        when(connectionsRepository.findAllByToBeConnectedWithUserId(anyLong())).thenReturn(List.of(connection));

        List<ConnectOutgoingMessageDTO> expectedOutgoingMessageDTOS = new ArrayList<>();
        ConnectOutgoingMessageDTO outgoingMessage = ConnectOutgoingMessageDTO.builder()
                .connectionSuccess(true)
                .to(getUsernameDTOForUserID(connection.getRequestingUserId()))
                .message("")
                .build();
        expectedOutgoingMessageDTOS.add(outgoingMessage);

        Mockito.when(userRepository.findById(Mockito.any())).thenReturn(Optional.of(getUser1())).thenReturn(Optional.of(getUser2()));
        List<ConnectOutgoingMessageDTO> actualMessageDTOs = connectService.getAllConnectionsForAUser(toBeConnectedUserId);

        assertThat(actualMessageDTOs).usingRecursiveComparison().isEqualTo(expectedOutgoingMessageDTOS);

    }

    @Test
    public void testGetAllConnectionsForAUserWhenConnectionsDoNotExist() {
        Long toBeConnectedUserId = USER2_ID;

        when(connectionsRepository.findAllByToBeConnectedWithUserId(anyLong())).thenReturn(Collections.emptyList());

        List<ConnectOutgoingMessageDTO> actualMessageDTOs = connectService.getAllConnectionsForAUser(toBeConnectedUserId);

        assertThat(actualMessageDTOs).usingRecursiveComparison().isEqualTo(Collections.emptyList());

    }

    @Test
    public void validateQRCodeWhenQRCodeIsEmpty() {
        String qrcodePhrase = "";
        Long userId = USER1_ID;
        when(cacheService.get(any(), any())).thenReturn("");
        boolean isValidQRCode = connectService.validateQRCode(qrcodePhrase, userId);
        assertFalse(isValidQRCode);
    }

    @Test
    public void validateQRCodeWhenQRCodeIsValid() {
        String qrcodePhrase = "ABCDE";
        Long userId = USER1_ID;
        when(cacheService.get(any(), any())).thenReturn(qrcodePhrase);
        boolean isValidQRCode = connectService.validateQRCode(qrcodePhrase, userId);
        assertTrue(isValidQRCode);
    }

    @Test
    public void validateQRCodeWhenNoQRCodeIsCached() {
        String qrcodePhrase = "ABCDE";
        Long userId = USER1_ID;
        when(cacheService.get(any(), any())).thenReturn(null);
        boolean isValidQRCode = connectService.validateQRCode(qrcodePhrase, userId);
        assertFalse(isValidQRCode);
    }
}
