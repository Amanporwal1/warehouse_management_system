package com.advatix.OMS.service;

import com.advatix.OMS.domain.Client;
import com.advatix.OMS.dto.ClientRequest;
import com.advatix.OMS.dto.ClientResponse;
import com.advatix.OMS.exception.BadRequestException;
import com.advatix.OMS.repository.ClientRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        if (clientRepository.existsByNameIgnoreCase(request.name())) {
            throw new BadRequestException("Client name already exists");
        }
        if (clientRepository.existsByClientCodeIgnoreCase(request.clientCode())) {
            throw new BadRequestException("Client code already exists");
        }

        Client client = clientRepository.save(Client.builder()
                .name(request.name().trim())
                .clientCode(request.clientCode().trim())
                .contactEmail(request.contactEmail().trim())
                .contactPhone(request.contactPhone().trim())
                .active(true)
                .build());

        return new ClientResponse(client.getId(), client.getName(), client.getClientCode(),
                client.getContactEmail(), client.getContactPhone(), client.isActive());
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> getClients() {
        return clientRepository.findAll().stream()
                .sorted(Comparator.comparing(Client::getName, String.CASE_INSENSITIVE_ORDER))
                .map(client -> new ClientResponse(client.getId(), client.getName(), client.getClientCode(),
                        client.getContactEmail(), client.getContactPhone(), client.isActive()))
                .toList();
    }
}
