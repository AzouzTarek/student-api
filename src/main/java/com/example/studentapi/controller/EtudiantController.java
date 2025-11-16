package com.example.studentapi.controller;

import com.example.studentapi.entity.Etudiant;
import com.example.studentapi.service.EtudiantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/etudiants")
@CrossOrigin("*")
public class EtudiantController {

    private final EtudiantService service;

    public EtudiantController(EtudiantService service) {
        this.service = service;
    }

    @GetMapping
    public List<Etudiant> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Etudiant getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public Etudiant create(@RequestBody Etudiant e) {
        return service.create(e);
    }

    @PutMapping("/{id}")
    public Etudiant update(@PathVariable Long id, @RequestBody Etudiant e) {
        return service.update(id, e);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
