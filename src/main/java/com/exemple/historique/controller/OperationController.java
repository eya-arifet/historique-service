package com.exemple.historique.controller;

import com.exemple.historique.client.CalculClient;
import com.exemple.historique.model.Operation;
import com.exemple.historique.service.OperationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/operations")
public class OperationController {
    private static final List<String> OPERATEURS =
            List.of("somme", "soustraction", "produit", "division");
    private final OperationService operationService;
    private final CalculClient calculClient;

    public OperationController(OperationService operationService,
                               CalculClient calculClient) {
        this.operationService = operationService;
        this.calculClient = calculClient;
    }

    // Demande le résultat à calcul-service et le range dans l'opération.
    // Retourne une réponse d'erreur si le calcul est impossible, null sinon.
    private ResponseEntity<Object> calculer(Operation op) {
        if (op.getOperateur() == null || !OPERATEURS.contains(op.getOperateur())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erreur", "Opérateur inconnu ou manquant"));
        }

        try {
            double resultat = calculClient.calculer(op.getOperateur(), op.getA(), op.getB());
            op.setResultat(resultat);
            return null; // Pas d'erreur, le calcul a réussi

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erreur", "Division par zéro impossible"));
            }
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("erreur", "Erreur lors de l'appel au calcul-service"));
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("erreur", "calcul-service est indisponible"));
        }
    }

    // POST /operations : crée une opération
    @PostMapping
    public ResponseEntity<Object> creer(@RequestBody Operation op) {
        ResponseEntity<Object> erreurResponse = calculer(op);
        if (erreurResponse != null) {
            return erreurResponse;
        }

        Operation savedOp = operationService.ajouter(op);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(savedOp.getId())
                .toUri();

        return ResponseEntity.created(location).body(savedOp);
    }

    // GET /operations : retourne toutes les opérations
    @GetMapping
    public List<Operation> lister() {
        return operationService.lister();
    }

    // GET /operations/{id} : retourne une opération
    @GetMapping("/{id}")
    public ResponseEntity<Operation> lire(@PathVariable("id") int id) {
        Operation op = operationService.lire(id);
        if (op == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(op);
    }
}