/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.exemple.service;

import com.exemple.model.Reservation;
import java.util.Optional;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 *
 * @author hp
 */
public class ReservationServiceImpl implements ReservationService {
    
    private final EntityManagerFactory emf;
    
    public ReservationServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }
    
    @Override
    public Reservation save(Reservation reservation) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(reservation);
            em.getTransaction().commit();
            return reservation;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
    
    @Override
    public Optional<Reservation> findById(Long id) {
        EntityManager em = emf.createEntityManager();
        try {
            Reservation reservation = em.find(Reservation.class, id);
            return Optional.ofNullable(reservation);
        } finally {
            em.close();
        }
    }
    
    @Override
    public void update(Reservation reservation) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(reservation);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
    
    @Override
    public void delete(Reservation reservation) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (!em.contains(reservation)) {
                reservation = em.merge(reservation);
            }
            em.remove(reservation);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        }
    }
