package com.arktech.superaccountant.gstr1.channel;

import com.arktech.superaccountant.gstr1.json.WriterOptions;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;

public interface Gstr1FilingChannel {

    ChannelKind kind();

    boolean supportsDirectSubmit();

    ChannelResult submit(PreparedReturn preparedReturn, WriterOptions options);
}
