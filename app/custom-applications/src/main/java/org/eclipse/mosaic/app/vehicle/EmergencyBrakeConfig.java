/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.app.vehicle;

/**
 * Default configuration for the {@link EmergencyBrakeApp}.
 */
public class EmergencyBrakeConfig {

    /**
     * The deceleration in m/s^2 with which the vehicle slows down in case an
     * obstacle is detected
     */
    public double deceleration = 5d;

    /**
     * The speed in m/s the vehicle is trying to reach during slow down in case an
     * obstacle is detected
     */
    public double targetSpeed = 2.0d;
}