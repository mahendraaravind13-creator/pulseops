package com.pulseops.analyzer.controller;

import com.pulseops.analyzer.repository.IncidentStepRepository;
import com.pulseops.analyzer.model.IncidentStep;
import com.pulseops.analyzer.model.Incident;
import com.pulseops.analyzer.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/incidents")
@CrossOrigin(origins = "*")
public class IncidentController {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentStepRepository stepRepository;

    // GET all incidents
    @GetMapping
    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    // GET all steps for one incident — used by the timeline panel
    @GetMapping("/{id}/steps")
    public ResponseEntity<List<IncidentStep>> getSteps(@PathVariable Long id) {
        List<IncidentStep> steps = stepRepository.findByIncidentIdOrderByStepNumberAsc(id);
        return ResponseEntity.ok(steps);
    }

    // GET incidents for one tenant
    @GetMapping("/tenant/{tenantId}")
    public List<Incident> getByTenant(@PathVariable String tenantId) {
        return incidentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    // GET one incident by ID
    @GetMapping("/{id}")
    public ResponseEntity<Incident> getById(@PathVariable Long id) {
        Optional<Incident> incident = incidentRepository.findById(id);
        return incident
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT acknowledge
    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<Incident> acknowledge(@PathVariable Long id) {
        Optional<Incident> optional = incidentRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Incident incident = optional.get();
        incident.setStatus(Incident.IncidentStatus.ACKNOWLEDGED);
        incident.setUpdatedAt(LocalDateTime.now());
        incidentRepository.save(incident);
        System.out.println("✅ Incident " + id + " acknowledged.");
        return ResponseEntity.ok(incident);
    }

    // PUT resolve with note
    @PutMapping("/{id}/resolve")
    public ResponseEntity<Incident> resolve(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Optional<Incident> optional = incidentRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Incident incident = optional.get();
        incident.setStatus(Incident.IncidentStatus.RESOLVED);
        incident.setResolutionNote(body.getOrDefault("note", "Resolved by engineer."));
        incident.setUpdatedAt(LocalDateTime.now());
        incidentRepository.save(incident);
        System.out.println("✅ Incident " + id + " resolved.");
        return ResponseEntity.ok(incident);
    }
}