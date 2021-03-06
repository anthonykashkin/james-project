/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.methods;

import java.io.Closeable;
import java.io.IOException;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetVacationRequest;
import org.apache.james.jmap.model.GetVacationResponse;
import org.apache.james.jmap.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.date.ZonedDateTimeProvider;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class GetVacationResponseMethod implements Method {

    public static final Request.Name METHOD_NAME = Request.name("getVacationResponse");
    public static final Response.Name RESPONSE_NAME = Response.name("vacationResponse");

    private final VacationRepository vacationRepository;
    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final MetricFactory metricFactory;

    @Inject
    public GetVacationResponseMethod(VacationRepository vacationRepository, ZonedDateTimeProvider zonedDateTimeProvider, MetricFactory metricFactory) {
        this.vacationRepository = vacationRepository;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.metricFactory = metricFactory;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetVacationRequest.class;
    }

    @Override
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(clientId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetVacationRequest);

        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + METHOD_NAME.getName());
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.ACTION, "VACATION")
                     .build()) {
            return Stream.of(JmapResponse.builder()
                .clientId(clientId)
                .responseName(RESPONSE_NAME)
                .response(process(mailboxSession))
                .build());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private GetVacationResponse process(MailboxSession mailboxSession) {
        Vacation vacation = vacationRepository.retrieveVacation(AccountId.fromString(mailboxSession.getUser().getUserName())).join();
        return GetVacationResponse.builder()
            .accountId(mailboxSession.getUser().getUserName())
            .vacationResponse(VacationResponse.builder()
                .fromVacation(vacation)
                .activated(vacation.isActiveAtDate(zonedDateTimeProvider.get()))
                .build())
            .build();
    }
}
