package com.github.nhordiienko23.travelideasai.repository;

import com.github.nhordiienko23.travelideasai.entity.SavedIdea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedIdeaRepository extends JpaRepository<SavedIdea, Long> {

    List<SavedIdea> findAllByOrderByCreatedAtDesc();

}