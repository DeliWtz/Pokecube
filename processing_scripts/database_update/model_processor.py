
def _process_burmy(entry, key, model):
    model['model'] = entry.name
    model['anim'] = entry.name
    model['key'] = key
    model['tex'] = key


def _process_arceus_silvally(entry, key, model):
    model['model'] = entry.name
    model['anim'] = key.replace('-', '_')
    model['key'] = key.replace('-', '_')
    model['tex'] = key.replace('-', '_')

PROCESSORS = {
    'arceus': _process_arceus_silvally,
    'silvally': _process_arceus_silvally,
    'burmy': _process_burmy,
}

def process_model(entry, key, model):
    if entry.name in PROCESSORS:
        PROCESSORS[entry.name](entry, key, model)