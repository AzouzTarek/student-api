package com.example.studentapi;

import com.example.studentapi.controller.EtudiantController;
import com.example.studentapi.entity.Etudiant;
import com.example.studentapi.service.EtudiantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EtudiantController.class)
class EtudiantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtudiantService etudiantService;

    @Test
    void testGetAll() throws Exception {
        Etudiant e = new Etudiant();
        e.setId(1L);
        e.setNom("Azouz");
        e.setPrenom("Tarek");
        e.setEmail("tarek@gmail.com");
        e.setNiveau("Licence 3");

        when(etudiantService.getAll()).thenReturn(List.of(e));

        mockMvc.perform(get("/etudiants")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("[{'id':1,'nom':'Azouz','prenom':'Tarek','email':'tarek@gmail.com','niveau':'Licence 3'}]"));
    }
}
