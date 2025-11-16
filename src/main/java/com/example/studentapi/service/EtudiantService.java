package com.example.studentapi.service;

import com.example.studentapi.entity.Etudiant;
import com.example.studentapi.repository.EtudiantRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EtudiantService {

    private final EtudiantRepository repository;

    public EtudiantService(EtudiantRepository repository) {
        this.repository = repository;
    }

    public List<Etudiant> getAll() {
        return repository.findAll();
    }

    public Etudiant getById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public Etudiant create(Etudiant etudiant) {
        return repository.save(etudiant);
    }

    public Etudiant update(Long id, Etudiant e) {
        Etudiant existing = getById(id);
        if (existing == null) return null;

        existing.setNom(e.getNom());
        existing.setPrenom(e.getPrenom());
        existing.setEmail(e.getEmail());
        existing.setNiveau(e.getNiveau());

        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}

