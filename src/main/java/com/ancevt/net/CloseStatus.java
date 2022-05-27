/**
 * Copyright (C) 2022 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 *     Copyright 2015-2022 Ancevt (me@ancevt.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "LICENSE");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ancevt.net;

public class CloseStatus {

    private Throwable throwable;

    public CloseStatus(Throwable throwable) {
        this.throwable = throwable;
    }

    public CloseStatus() {

    }

    public boolean isError() {
        return throwable != null;
    }

    public String getErrorMessage() {
        return isError() ? throwable.getMessage() : "";
    }

    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public String toString() {
        return "CloseStatus{" +
                "isError=" + isError() +
                ", throwable=" + throwable +
                '}';
    }
}