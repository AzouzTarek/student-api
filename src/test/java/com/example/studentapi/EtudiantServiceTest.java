package com.example.studentapi;

import com.example.studentapi.entity.Etudiant;
import com.example.studentapi.repository.EtudiantRepository;
import com.example.studentapi.service.EtudiantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EtudiantServiceTest {

    @Mock
    private EtudiantRepository repository;

    @InjectMocks
    private EtudiantService service;

    private Etudiant e1;
    private Etudiant e2;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        e1 = new Etudiant();
        e1.setId(1L);
        e1.setNom("Azouz");

        e2 = new Etudiant();
        e2.setId(2L);
        e2.setNom("Doe");
    }

    @Test
    void testGetAll() {
        when(repository.findAll()).thenReturn(Arrays.asList(e1, e2));
        List<Etudiant> result = service.getAll();
        assertEquals(2, result.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    void testGetById() {
        when(repository.findById(1L)).thenReturn(Optional.of(e1));
        Etudiant result = service.getById(1L);
        assertEquals("Azouz", result.getNom());
        verify(repository, times(1)).findById(1L);
    }

    @Test
    void testCreate() {
        when(repository.save(e1)).thenReturn(e1);
        Etudiant result = service.create(e1);
        assertNotNull(result);
        assertEquals("Azouz", result.getNom());
        verify(repository, times(1)).save(e1);
    }

    @Test
    void testUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(e1));
        when(repository.save(any(Etudiant.class))).thenReturn(e1);
        Etudiant updated = service.update(1L, e1);
        assertEquals("Azouz", updated.getNom());
        verify(repository, times(1)).save(e1);
    }

    @Test
    void testDelete() {
        doNothing().when(repository).deleteById(1L);
        service.delete(1L);
        verify(repository, times(1)).deleteById(1L);
    }
}
