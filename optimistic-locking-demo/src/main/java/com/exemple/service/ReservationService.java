/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.exemple.service;

import com.exemple.model.Reservation;
import java.util.Optional;

/**
 *
 * @author hp
 */
public interface ReservationService {
     Reservation save(Reservation reservation);
    Optional<Reservation> findById(Long id);
    void update(Reservation reservation);
    void delete(Reservation reservation);
}
