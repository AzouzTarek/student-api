package com.example.studentapi.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    private String prenom;
    private String email;
    private String niveau;
}
